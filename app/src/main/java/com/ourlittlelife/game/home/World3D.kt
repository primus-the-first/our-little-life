package com.ourlittlelife.game.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.ourlittlelife.rules.Area
import com.ourlittlelife.rules.Bed
import com.ourlittlelife.rules.Crop
import com.ourlittlelife.rules.Furniture
import com.ourlittlelife.rules.FurnitureKind
import com.ourlittlelife.rules.Interiors
import com.ourlittlelife.rules.Room
import com.ourlittlelife.rules.Gardening
import com.ourlittlelife.rules.Look
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.WorldMap
import io.github.sceneview.Scene
import io.github.sceneview.SceneView
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
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.utils.motionEventToRay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

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
    val shoes = Color(0xFF3E2723)
    val hair = Color(0xFF2B1B14)
    val eyes = Color(0xFF1A1A1A)
    val rug = Color(0xFFD9784A)
    val stove = Color(0xFF424242)
    val hob = Color(0xFF212121)
    val basin = Color(0xFFE0E0E0)
    val flame = Color(0xFFFFC107)
    val fits = Color(0xFF66BB6A)
    val blocked = Color(0xFFE57373)
    val selected = Color(0xFF2B2B2B)
    val tentDoor = Color(0xFF2E6E66)
    val stone = Color(0xFF8D8D8D)
    val log = Color(0xFF5D4037)
    val metal = Color(0xFF9E9E9E)
    val planks = Color(0xFFB08458)
    val raceBoard = Color(0xFFE57373)
    val trailBoard = Color(0xFF81C784)
}

/** What a person's body is doing: walking or standing on its own ([AUTO]), sitting, lying down, or busy with their hands. */
enum class Pose3D { AUTO, SIT, LIE, WORK }

/**
 * One person in the 3D world: where they stand, how they look, and their pose. [restAt] puts them
 * somewhere exact instead of on their tile, such as their side of the bed.
 */
data class Figure3D(
    val name: String,
    val x: Int,
    val y: Int,
    val look: Look,
    val pose: Pose3D = Pose3D.AUTO,
    val restAt: Position? = null,
)

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
    val (engine, models, materials) = Shared3D.get(LocalContext.current)

    // The land and everything on it, rebuilt only when the plot changes (something built, planted or placed).
    val worldNodes = rememberCached(plot) { buildWorld(engine, models, materials, plot) }

    TiltedScene(
        engine = engine,
        models = models,
        materials = materials,
        staticNodes = worldNodes,
        figures = figures,
        focus = focus,
        onTapTile = onTapTile,
        description = "Your world in 3D. Tap the ground to walk, or tap something to use it. Pinch to zoom, drag to look around.",
        modifier = modifier,
    )
}

/**
 * Inside a room in live 3D: a wooden floor, the two back walls full height and the two front walls cut
 * down low so you can see in, a doorway, the furniture, and whoever is in here. Taps find the floor
 * tile under your finger, exactly like the flat room, so furnishing and using things work unchanged.
 * [ghost] is the outline of a piece being placed; [selected] is the piece whose sheet is open.
 */
@Composable
fun Room3D(
    room: Room,
    figures: List<Figure3D>,
    focus: Pair<Int, Int>,
    selected: Furniture?,
    ghost: Ghost?,
    onTapTile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (engine, models, materials) = Shared3D.get(LocalContext.current)
    val roomNodes = rememberCached(room) { buildRoom(engine, models, materials, room) }
    val marks = rememberCached(selected to ghost) { buildMarks(engine, materials, selected, ghost) }
    TiltedScene(
        engine = engine,
        models = models,
        materials = materials,
        staticNodes = roomNodes + marks,
        figures = figures,
        focus = focus,
        onTapTile = onTapTile,
        description = "Inside the ${room.kind.label.lowercase()} in 3D. Tap furniture to use it, or the floor to walk. Pinch to zoom, drag to look around.",
        modifier = modifier,
        startZoom = 1.6f,
    )
}

/**
 * One 3D engine and model loader for the whole game, made the first time they're needed and kept for
 * as long as the app runs. Each view used to make its own and destroy it on closing, and going inside
 * or heading out would tear an engine down while the old view was still finishing a frame: a crash.
 */
private object Shared3D {
    private var made: Triple<Engine, ModelLoader, MaterialLoader>? = null

    /** Built nodes by what they show (a plot, a room, a person). The oldest are dropped from the cache when it's full. */
    private val built = object : LinkedHashMap<Any?, List<Node>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any?, List<Node>>?) = size > CACHE_SIZE
    }

    fun cached(key: Any?, build: () -> List<Node>): List<Node> = built.getOrPut(key, build)

    private val rigs = HashMap<Any, PersonRig>()

    /** A person's figure, reused for as long as their name and look stay the same. */
    fun rig(key: Any, build: () -> PersonRig): PersonRig = rigs.getOrPut(key, build)

    fun get(context: Context): Triple<Engine, ModelLoader, MaterialLoader> = made ?: run {
        val app = context.applicationContext
        val engine = SceneView.createEngine(SceneView.createEglContext())
        Triple(engine, SceneView.createModelLoader(engine, app), SceneView.createMaterialLoader(engine, app)).also { made = it }
    }
}

/**
 * The nodes for [key], built once and then kept and reused: going back into a room, or back out to the
 * world, shows the same models again instead of loading new copies. Nothing is freed, because freeing
 * models while views switch crashes the model loader; reuse keeps memory from growing instead. Only one
 * view is ever on screen, so a node is never in two scenes at once.
 */
@Composable
private fun rememberCached(key: Any?, build: () -> List<Node>): List<Node> = remember(key) { Shared3D.cached(key, build) }

/**
 * The shared camera and people for the world and rooms: a camera tilted down from the south-east that
 * glides after [focus], pinch to zoom, drag to look around (walking re-centres), people walking and
 * breathing, and taps turned into the tile under the finger where the ray meets the floor (y = 0).
 */
@Composable
private fun TiltedScene(
    engine: Engine,
    models: ModelLoader,
    materials: MaterialLoader,
    staticNodes: List<Node>,
    figures: List<Figure3D>,
    focus: Pair<Int, Int>,
    onTapTile: (Int, Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    startZoom: Float = 1f,
) {
    val view = rememberView(engine)

    // People walk from tile to tile in step with the game, turning to face where they're going.
    val people = figures.map { figure ->
        key(figure.name) {
            val at by animateOffsetAsState(
                targetValue = Offset(figure.x.toFloat(), figure.y.toFloat()),
                animationSpec = tween(WALK_STEP_MILLIS.toInt(), easing = LinearEasing),
                label = "walk3d-${figure.name}",
            )
            val rig = remember(figure.name, figure.look) { Shared3D.rig(figure.name to figure.look) { PersonRig(engine, materials, figure.look) } }
            val last = remember(figure.name) { floatArrayOf(at.x, at.y) }
            LaunchedEffect(at, rig, figure.pose, figure.restAt) {
                val dx = at.x - last[0]
                val dz = at.y - last[1]
                val rest = figure.restAt
                if (rest != null) {
                    rig.root.rotation = Rotation()  // lying in bed: head towards the back wall
                } else if (abs(dx) + abs(dz) > 1e-4f) {
                    rig.root.rotation = Rotation(y = Math.toDegrees(atan2(dx, dz).toDouble()).toFloat())
                }
                rig.moving = at.x != figure.x.toFloat() || at.y != figure.y.toFloat()
                rig.stance = figure.pose
                rig.lift = rest?.y ?: 0f
                rig.root.position = rest?.let { Position(it.x, 0f, it.z) } ?: Position(at.x + 0.5f, 0f, at.y + 0.5f)
                last[0] = at.x
                last[1] = at.y
            }
            // Limbs swing while walking and the chest rises and falls at rest, every frame.
            LaunchedEffect(rig) {
                val start = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    rig.pose((now - start) / 1_000_000_000f)
                }
            }
            rig.root
        }
    }

    val target by animateOffsetAsState(
        targetValue = Offset(focus.first + 0.5f, focus.second + 0.5f),
        animationSpec = tween(400),
        label = "camera",
    )
    var zoom by remember { mutableFloatStateOf(startZoom) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(focus) { pan = Offset.Zero }
    val camera = rememberCameraNode(engine)
    LaunchedEffect(target, zoom, pan) {
        val x = target.x + pan.x
        val z = target.y + pan.y
        camera.position = Position(x + CAMERA_BACK * zoom, CAMERA_HEIGHT * zoom, z + CAMERA_BACK * zoom)
        camera.lookAt(Position(x, 0.6f, z))
    }

    Scene(
        modifier = modifier.semantics { contentDescription = description },
        engine = engine,
        view = view,
        modelLoader = models,
        materialLoader = materials,
        cameraNode = camera,
        cameraManipulator = null,
        childNodes = staticNodes + people,
        onGestureListener = rememberOnGestureListener(
            onScale = { detector, _, _ ->
                zoom = (zoom / detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            },
            onScroll = { _, _, _, distance ->
                // The camera looks from the south-east, so screen right is world (+x, -z) and screen down is (+x, +z).
                val k = PAN_PER_PIXEL * zoom
                val right = distance.x * k
                val down = -distance.y * k
                pan = Offset(pan.x + (right - down) * 0.7071f, pan.y + (-right - down) * 0.7071f)
            },
            onSingleTapConfirmed = { event, _ ->
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
 * A person built from simple shapes until clothed character models arrive: shoes, legs, a torso in a
 * shirt colour, arms with hands, a head in their skin tone with eyes and hair (short for the first
 * body, longer for the second). Limbs hang from joints, so walking swings them like a real stride.
 */
private class PersonRig(engine: Engine, materials: MaterialLoader, look: Look) {
    val root = Node(engine)
    var moving = false
    var stance = Pose3D.AUTO

    /** How high the body lies when lying down: on a bed, or just above the ground. */
    var lift = 0f

    private val hips = Node(engine).also { it.parent = root }
    private val legs = listOf(-1f, 1f).map { side -> joint(engine, hips, Position(side * 0.09f, 0.48f, 0f)) }
    private val chest = Node(engine).also { it.parent = hips }
    private val arms = listOf(-1f, 1f).map { side -> joint(engine, chest, Position(side * 0.24f, 1.0f, 0f)) }

    init {
        val skin = materials.createColorInstance(if (look.skin == 1) Palette.skinLight else Palette.skinDark, roughness = 0.7f)
        val shirt = materials.createColorInstance(if (look.body == 1) Palette.shirtB else Palette.shirtA, roughness = 0.9f)
        val trousers = materials.createColorInstance(Palette.trousers, roughness = 0.9f)
        val shoes = materials.createColorInstance(Palette.shoes, roughness = 0.6f)
        val hair = materials.createColorInstance(Palette.hair, roughness = 0.8f)
        val eyes = materials.createColorInstance(Palette.eyes, roughness = 0.3f)

        legs.forEach { leg ->
            CylinderNode(engine, radius = 0.075f, height = 0.44f, center = Position(0f, -0.22f, 0f), materialInstance = trousers).parent = leg
            CubeNode(engine, size = Size(0.13f, 0.07f, 0.22f), center = Position(0f, -0.45f, 0.04f), materialInstance = shoes).parent = leg
        }
        // Torso: the shirt, rounded at the shoulders, over a waistband of trousers.
        CubeNode(engine, size = Size(0.34f, 0.12f, 0.2f), center = Position(0f, 0.53f, 0f), materialInstance = trousers).parent = chest
        CylinderNode(engine, radius = 0.17f, height = 0.46f, center = Position(0f, 0.8f, 0f), materialInstance = shirt).parent = chest
        SphereNode(engine, radius = 0.17f, center = Position(0f, 1.02f, 0f), materialInstance = shirt).parent = chest
        arms.forEach { arm ->
            CylinderNode(engine, radius = 0.055f, height = 0.38f, center = Position(0f, -0.19f, 0f), materialInstance = shirt).parent = arm
            SphereNode(engine, radius = 0.06f, center = Position(0f, -0.41f, 0f), materialInstance = skin).parent = arm
        }
        // Neck, head, eyes looking forward (+z), and hair.
        CylinderNode(engine, radius = 0.06f, height = 0.1f, center = Position(0f, 1.18f, 0f), materialInstance = skin).parent = chest
        SphereNode(engine, radius = 0.16f, center = Position(0f, 1.36f, 0f), materialInstance = skin).parent = chest
        listOf(-1f, 1f).forEach { side ->
            SphereNode(engine, radius = 0.025f, center = Position(side * 0.06f, 1.38f, 0.145f), materialInstance = eyes).parent = chest
        }
        SphereNode(engine, radius = 0.165f, center = Position(0f, 1.42f, -0.025f), materialInstance = hair).parent = chest
        if (look.body == 1) {
            // Longer hair, falling behind the shoulders.
            CubeNode(engine, size = Size(0.3f, 0.36f, 0.08f), center = Position(0f, 1.2f, -0.12f), materialInstance = hair).parent = chest
        }
    }

    /**
     * Walking swings legs and arms with a little bounce; sitting folds the legs forward; lying down
     * tips the whole body back; busy hands work up and down; standing still breathes gently.
     */
    fun pose(seconds: Float) {
        hips.rotation = Rotation()
        if (stance == Pose3D.LIE && !moving) {
            // Pivot at the feet so the body lies back along the bed, head towards -z, face up.
            hips.rotation = Rotation(x = -90f)
            hips.position = Position(0f, maxOf(lift, 0.12f), 0f)
            legs.forEach { it.rotation = Rotation() }
            arms[0].rotation = Rotation(z = 8f)
            arms[1].rotation = Rotation(z = -8f)
            chest.position = Position(0f, 0f, sin(seconds * 1.1f) * 0.01f)
            return
        }
        chest.position = Position()
        if (stance == Pose3D.SIT && !moving) {
            legs.forEach { it.rotation = Rotation(x = -85f) }
            arms.forEach { it.rotation = Rotation(x = -30f) }
            hips.position = Position(0f, sin(seconds * 1.6f) * 0.006f, 0f)
            return
        }
        if (stance == Pose3D.WORK && !moving) {
            val stroke = -55f + sin(seconds * 6f) * 35f
            legs[0].rotation = Rotation(x = 8f)
            legs[1].rotation = Rotation(x = -8f)
            arms.forEach { it.rotation = Rotation(x = stroke) }
            hips.position = Position(0f, abs(sin(seconds * 6f)) * 0.015f, 0f)
            return
        }
        if (moving) {
            val phase = sin(seconds * STRIDES_PER_SECOND * 2 * PI.toFloat())
            val swing = phase * 32f
            legs[0].rotation = Rotation(x = swing)
            legs[1].rotation = Rotation(x = -swing)
            arms[0].rotation = Rotation(x = -swing * 0.8f)
            arms[1].rotation = Rotation(x = swing * 0.8f)
            hips.position = Position(0f, abs(phase) * 0.04f, 0f)
        } else {
            legs.forEach { it.rotation = Rotation() }
            arms[0].rotation = Rotation(z = 4f)
            arms[1].rotation = Rotation(z = -4f)
            hips.position = Position(0f, sin(seconds * 1.6f) * 0.008f, 0f)
        }
    }

    private fun joint(engine: Engine, parent: Node, at: Position) = Node(engine).also {
        it.parent = parent
        it.position = at
    }
}

/** Strides per second at walking pace: one stride per tile, a tile per step of the game. */
private val STRIDES_PER_SECOND = 1000f / WALK_STEP_MILLIS / 2f

private const val CACHE_SIZE = 12
private const val MIN_ZOOM = 0.45f
private const val MAX_ZOOM = 3.2f
private const val PAN_PER_PIXEL = 0.012f
private const val CAMERA_BACK = 6f
private const val CAMERA_HEIGHT = 7.5f

/**
 * Stands a model on the ground at [at], centred on its real middle and turned [turn] degrees. (SceneView's
 * own centring only lowers a model by its height, so models whose middle isn't at their origin, like the
 * bed, ended up off to one side of their spot.)
 */
private fun placed(@Suppress("UNUSED_PARAMETER") engine: Engine, model: ModelNode, at: Position, turn: Float = 0f): Node {
    val centre = model.boundingBox.center
    val half = model.boundingBox.halfExtent
    val k = model.scale.x
    val ox = -centre[0] * k
    val oz = -centre[2] * k
    val radians = Math.toRadians(turn.toDouble())
    val c = kotlin.math.cos(radians).toFloat()
    val s = kotlin.math.sin(radians).toFloat()
    model.rotation = Rotation(y = turn)
    model.position = Position(at.x + ox * c + oz * s, -(centre[1] - half[1]) * k, at.z - ox * s + oz * c)
    return model
}

/**
 * Camp things built from simple shapes until they have models: an A-frame tent, a campfire of crossed
 * logs in a ring of stones, a workbench, a chest with a lid and a band, a washbasin, signposts, the
 * bench on the hill and a little jetty for fishing. [x], [z] is the object's corner; [w], [d] its size.
 */
private fun campThing(engine: Engine, mat: (Color) -> MaterialInstance, kind: ObjectKind, x: Float, z: Float, w: Float, d: Float): List<Node> {
    val parts = mutableListOf<Node>()
    fun put(node: Node, at: Position, turn: Rotation = Rotation()) {
        node.position = at
        node.rotation = turn
        parts += node
    }
    fun cube(sx: Float, sy: Float, sz: Float, c: Color) = CubeNode(engine, size = Size(sx, sy, sz), center = Position(0f), materialInstance = mat(c))
    fun rod(r: Float, h: Float, c: Color) = CylinderNode(engine, radius = r, height = h, center = Position(0f), materialInstance = mat(c))
    fun ball(r: Float, c: Color) = SphereNode(engine, radius = r, center = Position(0f), materialInstance = mat(c))
    val cx = x + w / 2
    val cz = z + d / 2

    when (kind) {
        ObjectKind.TENT -> {
            // A box turned 45° on its long edge, half sunk into the ground, makes the two sloping sides.
            put(cube(w - 0.3f, 1.25f, 1.25f, Palette.tent), Position(cx, 0f, cz), Rotation(x = 45f))
            put(cube(0.06f, 0.5f, 0.02f, Palette.tentDoor), Position(cx, 0.25f, cz + 0.89f))
            put(rod(0.03f, 1.0f, Palette.wood), Position(cx - (w - 0.3f) / 2 - 0.05f, 0.5f, cz))
            put(rod(0.03f, 1.0f, Palette.wood), Position(cx + (w - 0.3f) / 2 + 0.05f, 0.5f, cz))
        }
        ObjectKind.CAMPFIRE -> {
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                put(ball(0.08f, Palette.stone), Position(cx + 0.32f * kotlin.math.cos(a).toFloat(), 0.05f, cz + 0.32f * kotlin.math.sin(a).toFloat()))
            }
            put(rod(0.05f, 0.5f, Palette.log), Position(cx, 0.06f, cz), Rotation(z = 90f, y = 30f))
            put(rod(0.05f, 0.5f, Palette.log), Position(cx, 0.08f, cz), Rotation(z = 90f, y = -30f))
            put(ball(0.12f, Palette.fire), Position(cx, 0.17f, cz))
            put(ball(0.07f, Palette.flame), Position(cx, 0.28f, cz))
        }
        ObjectKind.WORKBENCH -> {
            put(cube(0.85f, 0.08f, 0.6f, Palette.wood), Position(cx, 0.72f, cz))
            for (sx in listOf(-0.35f, 0.35f)) for (sz in listOf(-0.22f, 0.22f)) put(cube(0.07f, 0.7f, 0.07f, Palette.log), Position(cx + sx, 0.35f, cz + sz))
            put(cube(0.18f, 0.14f, 0.14f, Palette.metal), Position(cx + 0.28f, 0.83f, cz))
            put(cube(0.3f, 0.06f, 0.08f, Palette.planks), Position(cx - 0.15f, 0.79f, cz - 0.1f))
        }
        ObjectKind.CHEST -> {
            put(cube(0.7f, 0.42f, 0.48f, Palette.wood), Position(cx, 0.21f, cz))
            put(cube(0.74f, 0.12f, 0.52f, Palette.log), Position(cx, 0.48f, cz))
            put(cube(0.08f, 0.56f, 0.53f, Palette.metal), Position(cx, 0.27f, cz))
        }
        ObjectKind.WASHBASIN -> {
            put(rod(0.06f, 0.7f, Palette.log), Position(cx, 0.35f, cz))
            put(rod(0.28f, 0.12f, Palette.basin), Position(cx, 0.76f, cz))
            put(rod(0.23f, 0.02f, Palette.water), Position(cx, 0.82f, cz))
        }
        ObjectKind.SIGNPOST, ObjectKind.RACE_SIGN, ObjectKind.TRAILHEAD -> {
            put(rod(0.05f, 1.3f, Palette.log), Position(cx, 0.65f, cz))
            val board = when (kind) {
                ObjectKind.RACE_SIGN -> Palette.raceBoard
                ObjectKind.TRAILHEAD -> Palette.trailBoard
                else -> Palette.planks
            }
            put(cube(0.62f, 0.26f, 0.05f, board), Position(cx + 0.12f, 1.05f, cz), Rotation(y = 20f))
        }
        ObjectKind.BENCH -> {
            put(cube(0.9f, 0.07f, 0.36f, Palette.planks), Position(cx, 0.42f, cz))
            put(cube(0.9f, 0.3f, 0.06f, Palette.planks), Position(cx, 0.65f, cz - 0.17f), Rotation(x = -10f))
            for (sx in listOf(-0.38f, 0.38f)) put(cube(0.07f, 0.42f, 0.32f, Palette.log), Position(cx + sx, 0.21f, cz))
        }
        ObjectKind.FISHING_SPOT -> {
            put(cube(0.95f, 0.08f, 0.95f, Palette.planks), Position(cx, 0.08f, cz))
            for (sx in listOf(-0.4f, 0.4f)) for (sz in listOf(-0.4f, 0.4f)) put(rod(0.04f, 0.3f, Palette.log), Position(cx + sx, 0f, cz + sz))
            put(rod(0.015f, 1.4f, Palette.log), Position(cx - 0.35f, 0.6f, cz), Rotation(z = 55f))
            put(cube(0.25f, 0.18f, 0.18f, Palette.raceBoard), Position(cx + 0.25f, 0.21f, cz + 0.2f))
        }
        else -> put(cube(w - 0.3f, 0.6f, d - 0.3f, Palette.wood), Position(cx, 0.3f, cz))
    }
    return parts
}

/** Furniture models (assets/models) for the pieces that have one; the rest are drawn as simple shapes. */
private fun furnitureModel(kind: FurnitureKind): String? = when (kind) {
    FurnitureKind.BED -> "bed_double"
    FurnitureKind.CHAIR -> "chair"
    FurnitureKind.SOFA -> "sofa"
    FurnitureKind.TABLE -> "table"
    FurnitureKind.BOOKSHELF -> "bookcase"
    FurnitureKind.PLANT -> "plant"
    else -> null
}

/** How big a furniture model is drawn: its largest side, in tiles. Tall pieces stand taller than their footprint. */
private fun furnitureSize(kind: FurnitureKind): Float = when (kind) {
    FurnitureKind.BOOKSHELF -> 1.7f
    FurnitureKind.PLANT -> 0.9f
    else -> maxOf(kind.width, kind.height) * 0.95f
}

/** The room itself: floor, walls (back ones tall, front ones low), doorway, and its furniture. */
private fun buildRoom(engine: Engine, models: ModelLoader, materials: MaterialLoader, room: Room): List<Node> {
    val nodes = mutableListOf<Node>()
    val colours = HashMap<Color, MaterialInstance>()
    fun mat(c: Color) = colours.getOrPut(c) { materials.createColorInstance(c, roughness = 0.85f) }
    fun box(x: Float, z: Float, w: Float, d: Float, height: Float, colour: Color, lift: Float = 0f) {
        nodes += CubeNode(engine, size = Size(w, height, d), center = Position(x + w / 2, lift + height / 2, z + d / 2), materialInstance = mat(colour))
    }

    val w = Interiors.width(room.kind).toFloat()
    val d = Interiors.height(room.kind).toFloat()
    val wall = 0.15f
    val tall = 2.4f
    val low = 0.35f
    val door = Interiors.door(room.kind).first.toFloat()

    // A little ground around the room, so it doesn't float in the dark.
    box(-3f, -3f, w + 6f, d + 6f, 0.1f, Palette.grass, lift = -0.12f)
    box(0f, 0f, w, d, 0.04f, Palette.floor, lift = -0.04f)
    // Back walls: tall, along the far side (z = 0) and the left side (x = 0).
    box(-wall, -wall, w + wall * 2, wall, tall, Palette.wall)
    box(-wall, 0f, wall, d, tall, Palette.wall)
    // Front walls: low, so the camera sees over them; the front one has a gap for the door.
    box(w, 0f, wall, d, low, Palette.wall)
    box(-wall, d, door + wall, wall, low, Palette.wall)
    box(door + 1f, d, w - door - 1f + wall, wall, low, Palette.wall)

    val placements = HashMap<String, MutableList<Furniture>>()
    room.furniture.forEach { piece ->
        val x = piece.x.toFloat()
        val z = piece.y.toFloat()
        val pw = piece.kind.width.toFloat()
        val pd = piece.kind.height.toFloat()
        val model = furnitureModel(piece.kind)
        when {
            model != null -> placements.getOrPut(model) { mutableListOf() } += piece
            piece.kind == FurnitureKind.RUG -> box(x + 0.05f, z + 0.05f, pw - 0.1f, pd - 0.1f, 0.02f, Palette.rug)
            piece.kind == FurnitureKind.STOVE -> {
                box(x + 0.08f, z + 0.08f, pw - 0.16f, pd - 0.16f, 0.85f, Palette.stove)
                box(x + 0.2f, z + 0.2f, pw - 0.4f, pd - 0.4f, 0.03f, Palette.hob, lift = 0.85f)
            }
            piece.kind == FurnitureKind.WASHSTAND -> {
                box(x + 0.1f, z + 0.1f, pw - 0.2f, pd - 0.2f, 0.75f, Palette.wood)
                box(x + 0.22f, z + 0.22f, pw - 0.44f, pd - 0.44f, 0.08f, Palette.basin, lift = 0.75f)
            }
            piece.kind == FurnitureKind.CANDLES -> {
                box(x + 0.4f, z + 0.4f, 0.2f, 0.2f, 0.9f, Palette.wood)
                box(x + 0.44f, z + 0.44f, 0.12f, 0.12f, 0.12f, Palette.flame, lift = 0.9f)
            }
            else -> box(x + 0.1f, z + 0.1f, pw - 0.2f, pd - 0.2f, 0.6f, Palette.wood)
        }
    }
    placements.forEach { (file, pieces) ->
        models.createInstancedModel("models/$file.glb", pieces.size).zip(pieces).forEach { (instance, piece) ->
            // The table model runs front to back; its spot in the game runs left to right.
            nodes += placed(
                engine,
                ModelNode(instance, autoAnimate = false, scaleToUnits = furnitureSize(piece.kind)),
                Position(piece.x + piece.kind.width / 2f, 0f, piece.y + piece.kind.height / 2f),
                turn = if (piece.kind == FurnitureKind.TABLE) 90f else 0f,
            )
        }
    }
    return nodes
}

/** The outline of a piece being placed (green if it fits, berry if not) and a marker on the selected piece. */
private fun buildMarks(engine: Engine, materials: MaterialLoader, selected: Furniture?, ghost: Ghost?): List<Node> {
    val nodes = mutableListOf<Node>()
    ghost?.let { (area, fits) ->
        val colour = (if (fits) Palette.fits else Palette.blocked).copy(alpha = 0.45f)
        nodes += CubeNode(
            engine,
            size = Size(area.width.toFloat(), 0.9f, area.height.toFloat()),
            center = Position(area.x + area.width / 2f, 0.45f, area.y + area.height / 2f),
            materialInstance = materials.createColorInstance(colour),
        )
    }
    selected?.let { piece ->
        nodes += CubeNode(
            engine,
            size = Size(piece.kind.width.toFloat(), 0.03f, piece.kind.height.toFloat()),
            center = Position(piece.x + piece.kind.width / 2f, 0.015f, piece.y + piece.kind.height / 2f),
            materialInstance = materials.createColorInstance(Palette.selected),
        )
    }
    return nodes
}

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
            else -> nodes += campThing(engine, ::mat, obj.kind, x, z, w, d)
        }
    }

    placements.forEach { (file, spots) ->
        val instances = models.createInstancedModel("models/$file.glb", spots.size)
        instances.zip(spots).forEach { (instance, spot) ->
            val (x, z, turn) = spot
            nodes += placed(engine, ModelNode(instance, autoAnimate = false, scaleToUnits = Models.size(file)), Position(x, 0f, z), turn)
        }
    }
    return nodes
}
