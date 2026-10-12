package com.ourlittlelife.game.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.android.filament.Engine
import com.ourlittlelife.rules.Area
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.WorldMap
import io.github.sceneview.Scene
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.utils.motionEventToRay
import kotlin.math.floor

/**
 * World art for the 3D view: these are the colours of things in the world, not the interface, so
 * they live here rather than in the theme. They stand in until the Kenney 3D models arrive.
 */
private object Palette {
    val grass = Color(0xFF7CB342)
    val dirt = Color(0xFFA1785A)
    val path = Color(0xFFE3D3A8)
    val water = Color(0xFF4FA3C7)
    val hill = Color(0xFF689F38)
    val flowers = Color(0xFFF48FB1)
    val track = Color(0xFFB07A4F)
    val fence = Color(0xFF8A5A3C)
    val floor = Color(0xFFC8A06E)
    val wall = Color(0xFFEEE3D0)
    val trunk = Color(0xFF6D4C41)
    val leaves = Color(0xFF388E3C)
    val rock = Color(0xFF9E9E9E)
    val bush = Color(0xFF43A047)
    val tent = Color(0xFF4DB6AC)
    val fire = Color(0xFFFF7043)
    val wood = Color(0xFF8D6E63)
    val skin = Color(0xFFE0B48C)
}

/** One person to stand in the 3D world: where they are, and the colour of their clothes. */
data class Figure3D(val name: String, val x: Int, val y: Int, val shirt: Color)

/**
 * The world in 3D: the same tiles, objects and rooms as the flat map, seen from a tilted camera
 * that follows [focus]. Tapping the ground finds the tile under your finger, exactly like the flat
 * map, so every rule and action works unchanged. One tile is one metre.
 */
@Composable
fun World3D(
    plot: Plot,
    figures: List<Figure3D>,
    focus: Pair<Int, Int>,
    onTapTile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materials = rememberMaterialLoader(engine)
    val view = rememberView(engine)

    // The world itself only changes when the plot does (something built, gathered or placed).
    val worldNodes = remember(plot) { buildWorld(engine, materials, plot) }

    // People glide from tile to tile in step with the walking speed, like on the flat map.
    val people = figures.map { figure ->
        key(figure.name) {
            val at by animateOffsetAsState(
                targetValue = Offset(figure.x.toFloat(), figure.y.toFloat()),
                animationSpec = tween(WALK_STEP_MILLIS.toInt(), easing = LinearEasing),
                label = "walk3d-${figure.name}",
            )
            val body = remember(figure.name) { person(engine, materials, figure.shirt) }
            LaunchedEffect(at) { body.position = Position(at.x + 0.5f, 0f, at.y + 0.5f) }
            body
        }
    }

    // A fixed, tilted camera a little way off to the south-east, gliding after whoever it follows.
    val target by animateOffsetAsState(
        targetValue = Offset(focus.first + 0.5f, focus.second + 0.5f),
        animationSpec = tween(400),
        label = "camera",
    )
    val camera = rememberCameraNode(engine)
    LaunchedEffect(target) {
        camera.position = Position(target.x + CAMERA_BACK, CAMERA_HEIGHT, target.y + CAMERA_BACK)
        camera.lookAt(Position(target.x, 0f, target.y))
    }

    Scene(
        modifier = modifier.semantics { contentDescription = "Your world in 3D. Tap the ground to walk, or tap something to use it." },
        engine = engine,
        view = view,
        materialLoader = materials,
        cameraNode = camera,
        cameraManipulator = null,
        childNodes = worldNodes + people,
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { event, _ ->
                // Where the ray from the finger meets the ground (y = 0) is the tile that was tapped.
                val ray = view.motionEventToRay(event)
                val dy = ray.direction.y
                if (dy < -1e-4f) {
                    val t = -ray.origin.y / dy
                    val hit = ray.origin + ray.direction * t
                    onTapTile(floor(hit.x).toInt(), floor(hit.z).toInt())
                }
            },
        ),
    )
}

private const val CAMERA_BACK = 7f
private const val CAMERA_HEIGHT = 9f

/** A simple person: a body in their shirt colour and a head. Replaced by Kenney's Mini Characters next. */
private fun person(engine: Engine, materials: MaterialLoader, shirt: Color): Node {
    val root = Node(engine)
    CylinderNode(engine, radius = 0.22f, height = 0.7f, center = Position(0f, 0.35f, 0f), materialInstance = materials.createColorInstance(shirt))
        .also { it.parent = root }
    SphereNode(engine, radius = 0.18f, center = Position(0f, 0.88f, 0f), materialInstance = materials.createColorInstance(Palette.skin))
        .also { it.parent = root }
    return root
}

/** Every static thing in the world, built once per change to the plot. */
private fun buildWorld(engine: Engine, materials: MaterialLoader, plot: Plot): List<Node> {
    val nodes = mutableListOf<Node>()
    val colours = HashMap<Color, com.google.android.filament.MaterialInstance>()
    fun mat(c: Color) = colours.getOrPut(c) { materials.createColorInstance(c, roughness = 0.9f) }

    /** A box sitting on the ground, [height] tall, covering [w] × [d] tiles from (x, z). */
    fun box(x: Float, z: Float, w: Float, d: Float, height: Float, colour: Color, lift: Float = 0f) {
        nodes += CubeNode(
            engine,
            size = Size(w, height, d),
            center = Position(x + w / 2, lift + height / 2, z + d / 2),
            materialInstance = mat(colour),
        )
    }

    fun area(a: Area, colour: Color, height: Float = 0.02f) = box(a.x.toFloat(), a.y.toFloat(), a.width.toFloat(), a.height.toFloat(), height, colour)

    // The ground, then the land on top of it.
    box(0f, 0f, plot.size.toFloat(), plot.size.toFloat(), 0.1f, Palette.grass, lift = -0.1f)
    if (plot.surroundings) {
        WorldMap.FLOWER_BEDS.forEach { area(it, Palette.flowers) }
        area(WorldMap.HILL, Palette.hill, height = 0.25f)
        WorldMap.PATHS.forEach { area(it, Palette.path, height = 0.03f) }
        area(WorldMap.POND, Palette.water, height = 0.01f)
        val course = WorldMap.RACECOURSE
        area(course, Palette.track, height = 0.02f)
        // A low fence all the way round the racecourse.
        box(course.x.toFloat(), course.y.toFloat(), course.width.toFloat(), 0.1f, 0.5f, Palette.fence)
        box(course.x.toFloat(), (course.y + course.height).toFloat() - 0.1f, course.width.toFloat(), 0.1f, 0.5f, Palette.fence)
        box(course.x.toFloat(), course.y.toFloat(), 0.1f, course.height.toFloat(), 0.5f, Palette.fence)
        box((course.x + course.width).toFloat() - 0.1f, course.y.toFloat(), 0.1f, course.height.toFloat(), 0.5f, Palette.fence)
    }
    // The bare earth of the camp.
    box(plot.yard.x + 8f, plot.yard.y + 8f, 8f, 6f, 0.025f, Palette.dirt)

    // Rooms: a floor and walls, with a gap in the bottom wall for the door.
    plot.rooms.forEach { room ->
        val x = room.x.toFloat()
        val z = room.y.toFloat()
        val w = room.kind.width.toFloat()
        val d = room.kind.height.toFloat()
        if (room.kind == RoomKind.GARDEN) {
            box(x, z, w, d, 0.06f, Palette.dirt)
            box(x, z, w, 0.08f, 0.4f, Palette.fence)
            box(x, z, 0.08f, d, 0.4f, Palette.fence)
            box(x + w - 0.08f, z, 0.08f, d, 0.4f, Palette.fence)
        } else {
            val wallHeight = 1.2f
            box(x, z, w, d, 0.05f, Palette.floor)
            box(x, z, w, 0.12f, wallHeight, Palette.wall)
            box(x, z, 0.12f, d, wallHeight, Palette.wall)
            box(x + w - 0.12f, z, 0.12f, d, wallHeight, Palette.wall)
            val door = w / 2 - 0.5f
            box(x, z + d - 0.12f, door, 0.12f, wallHeight, Palette.wall)
            box(x + door + 1f, z + d - 0.12f, w - door - 1f, 0.12f, wallHeight, Palette.wall)
        }
    }

    // Everything standing on the land.
    plot.objects.forEach { obj ->
        val x = obj.x.toFloat()
        val z = obj.y.toFloat()
        val w = obj.kind.width.toFloat()
        val d = obj.kind.height.toFloat()
        when (obj.kind) {
            ObjectKind.TREE -> {
                nodes += CylinderNode(engine, radius = 0.12f, height = 0.9f, center = Position(x + 0.5f, 0.45f, z + 0.5f), materialInstance = mat(Palette.trunk))
                nodes += SphereNode(engine, radius = 0.55f, center = Position(x + 0.5f, 1.3f, z + 0.5f), materialInstance = mat(Palette.leaves))
            }
            ObjectKind.ROCK -> nodes += SphereNode(engine, radius = 0.38f, center = Position(x + 0.5f, 0.2f, z + 0.5f), materialInstance = mat(Palette.rock))
            ObjectKind.BUSH -> nodes += SphereNode(engine, radius = 0.35f, center = Position(x + 0.5f, 0.3f, z + 0.5f), materialInstance = mat(Palette.bush))
            ObjectKind.TENT -> box(x + 0.1f, z + 0.1f, w - 0.2f, d - 0.2f, 1.1f, Palette.tent)
            ObjectKind.CAMPFIRE -> box(x + 0.3f, z + 0.3f, 0.4f, 0.4f, 0.25f, Palette.fire)
            else -> box(x + 0.15f, z + 0.15f, w - 0.3f, d - 0.3f, 0.6f, Palette.wood)
        }
    }
    return nodes
}
