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
import com.google.android.filament.MaterialInstance
import com.ourlittlelife.rules.Area
import com.ourlittlelife.rules.Bed
import com.ourlittlelife.rules.Crop
import com.ourlittlelife.rules.Gardening
import com.ourlittlelife.rules.Look
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.WorldMap
import io.github.sceneview.Scene
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.SphereNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.utils.motionEventToRay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor

/**
 * World art for the 3D view: the colours of the land and of things not yet modelled. They are the
 * world's colours, not the interface's, so they live here rather than in the theme.
 */
private object Palette {
    val grass = Color(0xFF7CB342)
    val dirt = Color(0xFFA1785A)
    val path = Color(0xFFE3D3A8)
    val water = Color(0xFF4FA3C7)
    val hill = Color(0xFF689F38)
    val track = Color(0xFFB07A4F)
    val fence = Color(0xFF8A5A3C)
    val floor = Color(0xFFC8A06E)
    val wall = Color(0xFFEEE3D0)
    val tent = Color(0xFF4DB6AC)
    val fire = Color(0xFFFF7043)
    val wood = Color(0xFF8D6E63)
    val shirtA = Color(0xFFE57373)
    val shirtB = Color(0xFF64B5F6)
    val trousers = Color(0xFF455A64)
    val skinDark = Color(0xFF7B4A2E)
    val skinLight = Color(0xFFE8C2A0)
}

/** One person in the 3D world: where they stand and how they look. */
data class Figure3D(val name: String, val x: Int, val y: Int, val look: Look)

/** Model files (in assets/models) and the size each is drawn at, in tiles. */
private object Models {
    val trees = listOf("tree_1", "tree_2", "tree_3", "pine_1", "pine_2")
    val rocks = listOf("rock_1", "rock_2", "rock_3")
    val bushes = listOf("bush_flowers", "bush")
    val flowers = listOf("flowers_1", "flowers_2")
    fun crop(bed: Bed): String? {
        val crop = bed.crop ?: return null
        val stage = when {
            Gardening.ready(bed) -> 4
            bed.grown == 0 -> 1
            bed.grown * 2 < crop.days -> 2
            else -> 3
        }
        return "${crop.name.lowercase()}_$stage"
    }
    fun size(name: String): Float = when {
        name.startsWith("tree") -> 3.0f
        name.startsWith("pine") -> 3.4f
        name.startsWith("rock") -> 0.9f
        name.startsWith("bush") -> 1.0f
        name.startsWith("flowers") -> 0.6f
        name.startsWith(Crop.PUMPKIN.name.lowercase()) -> 0.8f
        else -> 0.6f
    }
}

/**
 * The world in live 3D: the same tiles, objects and rooms as the flat map, seen from a tilted camera
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
    val models = rememberModelLoader(engine)
    val materials = rememberMaterialLoader(engine)
    val view = rememberView(engine)

    // The land and everything on it, rebuilt only when the plot changes (something built, planted or placed).
    val worldNodes = remember(plot) { buildWorld(engine, models, materials, plot) }

    // People walk from tile to tile in step with the game, turning to face where they're going.
    val people = figures.map { figure ->
        key(figure.name) {
            val at by animateOffsetAsState(
                targetValue = Offset(figure.x.toFloat(), figure.y.toFloat()),
                animationSpec = tween(WALK_STEP_MILLIS.toInt(), easing = LinearEasing),
                label = "walk3d-${figure.name}",
            )
            val body = remember(figure.name, figure.look) { standIn(engine, materials, figure.look) }
            val last = remember(figure.name) { floatArrayOf(at.x, at.y) }
            LaunchedEffect(at, body) {
                val dx = at.x - last[0]
                val dz = at.y - last[1]
                if (abs(dx) + abs(dz) > 1e-4f) {
                    body.rotation = Rotation(y = Math.toDegrees(atan2(dx, dz).toDouble()).toFloat())
                }
                body.position = Position(at.x + 0.5f, 0f, at.y + 0.5f)
                last[0] = at.x
                last[1] = at.y
            }
            body
        }
    }

    // A tilted camera a little way off to the south-east, gliding after whoever it follows.
    val target by animateOffsetAsState(
        targetValue = Offset(focus.first + 0.5f, focus.second + 0.5f),
        animationSpec = tween(400),
        label = "camera",
    )
    val camera = rememberCameraNode(engine)
    LaunchedEffect(target) {
        camera.position = Position(target.x + CAMERA_BACK, CAMERA_HEIGHT, target.y + CAMERA_BACK)
        camera.lookAt(Position(target.x, 0.6f, target.y))
    }

    Scene(
        modifier = modifier.semantics { contentDescription = "Your world in 3D. Tap the ground to walk, or tap something to use it." },
        engine = engine,
        view = view,
        modelLoader = models,
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

/**
 * A stand-in person until clothed character models arrive: a body in a shirt colour for their body
 * choice, and a head in their skin tone.
 */
private fun standIn(engine: Engine, materials: MaterialLoader, look: Look): Node {
    val root = Node(engine)
    val shirt = if (look.body == 1) Palette.shirtB else Palette.shirtA
    val skin = if (look.skin == 1) Palette.skinLight else Palette.skinDark
    CylinderNode(engine, radius = 0.2f, height = 0.55f, center = Position(0f, 0.75f, 0f), materialInstance = materials.createColorInstance(shirt)).parent = root
    CylinderNode(engine, radius = 0.17f, height = 0.5f, center = Position(0f, 0.25f, 0f), materialInstance = materials.createColorInstance(Palette.trousers)).parent = root
    SphereNode(engine, radius = 0.17f, center = Position(0f, 1.2f, 0f), materialInstance = materials.createColorInstance(skin)).parent = root
    return root
}

private const val CAMERA_BACK = 6f
private const val CAMERA_HEIGHT = 7.5f

/** Every static thing in the world, built once per change to the plot. */
private fun buildWorld(engine: Engine, models: ModelLoader, materials: MaterialLoader, plot: Plot): List<Node> {
    val nodes = mutableListOf<Node>()
    val colours = HashMap<Color, MaterialInstance>()
    fun mat(c: Color) = colours.getOrPut(c) { materials.createColorInstance(c, roughness = 0.9f) }

    /** A box sitting on the ground, [height] tall, covering [w] × [d] tiles from (x, z). */
    fun box(x: Float, z: Float, w: Float, d: Float, height: Float, colour: Color, lift: Float = 0f) {
        nodes += CubeNode(engine, size = Size(w, height, d), center = Position(x + w / 2, lift + height / 2, z + d / 2), materialInstance = mat(colour))
    }

    fun area(a: Area, colour: Color, height: Float = 0.02f) = box(a.x.toFloat(), a.y.toFloat(), a.width.toFloat(), a.height.toFloat(), height, colour)

    // Models placed on tiles: gathered by file first, so each file is loaded once and drawn many times.
    val placements = HashMap<String, MutableList<Triple<Float, Float, Float>>>()  // file -> (x, z, turn)
    fun place(file: String, x: Float, z: Float, turn: Float = 0f) {
        placements.getOrPut(file) { mutableListOf() } += Triple(x, z, turn)
    }

    // The ground, then the land on top of it.
    box(0f, 0f, plot.size.toFloat(), plot.size.toFloat(), 0.1f, Palette.grass, lift = -0.1f)
    if (plot.surroundings) {
        WorldMap.FLOWER_BEDS.forEach { bed ->
            for (x in bed.x until bed.x + bed.width) for (y in bed.y until bed.y + bed.height) {
                place(Models.flowers[(x + y) % Models.flowers.size], x + 0.5f, y + 0.5f, ((x * 37 + y * 11) % 360).toFloat())
            }
        }
        area(WorldMap.HILL, Palette.hill, height = 0.25f)
        WorldMap.PATHS.forEach { area(it, Palette.path, height = 0.03f) }
        area(WorldMap.POND, Palette.water, height = 0.01f)
        val course = WorldMap.RACECOURSE
        area(course, Palette.track, height = 0.02f)
        box(course.x.toFloat(), course.y.toFloat(), course.width.toFloat(), 0.1f, 0.5f, Palette.fence)
        box(course.x.toFloat(), (course.y + course.height).toFloat() - 0.1f, course.width.toFloat(), 0.1f, 0.5f, Palette.fence)
        box(course.x.toFloat(), course.y.toFloat(), 0.1f, course.height.toFloat(), 0.5f, Palette.fence)
        box((course.x + course.width).toFloat() - 0.1f, course.y.toFloat(), 0.1f, course.height.toFloat(), 0.5f, Palette.fence)
    }
    box(plot.yard.x + 8f, plot.yard.y + 8f, 8f, 6f, 0.025f, Palette.dirt)

    // Rooms: a floor and walls with a doorway; gardens get a low fence and whatever is growing.
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
            room.beds.forEachIndexed { i, bed ->
                val (dx, dy) = Sprites.bedTile(i)
                Models.crop(bed)?.let { place(it, x + dx + 0.5f, z + dy + 0.5f) }
            }
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

    // Nature from the model packs; camp things keep simple shapes until they're modelled.
    plot.objects.forEach { obj ->
        val x = obj.x.toFloat()
        val z = obj.y.toFloat()
        val w = obj.kind.width.toFloat()
        val d = obj.kind.height.toFloat()
        val variety = obj.id.hashCode().let { if (it < 0) -it else it }
        val turn = (variety % 360).toFloat()
        when (obj.kind) {
            ObjectKind.TREE -> place(Models.trees[variety % Models.trees.size], x + 0.5f, z + 0.5f, turn)
            ObjectKind.ROCK -> place(Models.rocks[variety % Models.rocks.size], x + 0.5f, z + 0.5f, turn)
            ObjectKind.BUSH -> place(Models.bushes[variety % Models.bushes.size], x + 0.5f, z + 0.5f, turn)
            ObjectKind.TENT -> box(x + 0.1f, z + 0.1f, w - 0.2f, d - 0.2f, 1.1f, Palette.tent)
            ObjectKind.CAMPFIRE -> box(x + 0.3f, z + 0.3f, 0.4f, 0.4f, 0.25f, Palette.fire)
            else -> box(x + 0.15f, z + 0.15f, w - 0.3f, d - 0.3f, 0.6f, Palette.wood)
        }
    }

    placements.forEach { (file, spots) ->
        val instances = models.createInstancedModel("models/$file.glb", spots.size)
        instances.zip(spots).forEach { (instance, spot) ->
            val (x, z, turn) = spot
            nodes += ModelNode(instance, autoAnimate = false, scaleToUnits = Models.size(file), centerOrigin = Position(0f, -1f, 0f)).apply {
                position = Position(x, 0f, z)
                rotation = Rotation(y = turn)
            }
        }
    }
    return nodes
}
