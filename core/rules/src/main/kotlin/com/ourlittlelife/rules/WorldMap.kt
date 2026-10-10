package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import kotlinx.serialization.Serializable

/** A rectangle of tiles. */
@Serializable
data class Area(val x: Int, val y: Int, val width: Int, val height: Int) {

    fun contains(tileX: Int, tileY: Int): Boolean = tileX in x until x + width && tileY in y until y + height

    fun contains(other: Area): Boolean =
        other.x >= x && other.y >= y && other.x + other.width <= x + width && other.y + other.height <= y + height
}

/** What the ground is made of. Water and the fenced racecourse can't be walked on. */
enum class Terrain(val blocks: Boolean = false) {
    GRASS,
    PATH,
    HILL,
    FLOWERS,
    WATER(blocks = true),
    TRACK(blocks = true),
}

/** Things to do out in the wider world. */
object Outdoors {
    val sit = Action("sit_bench", "Sit a while", 30, mapOf(FUN to 8, ENERGY to 4))
}

/**
 * The wider world around your yard: a stargazing hill to the north, a pond to the west,
 * a forest trail to the south, and the east side kept for a racecourse.
 * The terrain is fixed, so it is worked out here rather than saved with every world.
 */
object WorldMap {
    const val SIZE = 64

    /** Your own land, where rooms go. It's the old 24×24 plot, set in the middle-west. */
    val YARD = Area(20, 20, StarterPlot.SIZE, StarterPlot.SIZE)

    val HILL = Area(27, 4, 10, 7)
    val POND = Area(4, 23, 9, 6)

    /** Fenced off until races arrive. */
    val RACECOURSE = Area(47, 4, 15, 56)

    /** Out of the yard north to the hill, west to the pond, east to the racecourse gate, and south round the forest loop. */
    val PATHS = listOf(
        Area(31, 11, 1, 9),
        Area(13, 27, 7, 1),
        Area(44, 31, 3, 1),
        Area(31, 44, 1, 5),
        Area(10, 49, 22, 1),
        Area(10, 50, 1, 9),
        Area(31, 50, 1, 9),
        Area(10, 59, 22, 1),
    )

    val FLOWER_BEDS = listOf(
        Area(14, 8, 4, 3),
        Area(21, 14, 3, 2),
        Area(39, 12, 4, 2),
        Area(4, 34, 3, 3),
        Area(36, 52, 4, 3),
    )

    fun terrainAt(x: Int, y: Int): Terrain = when {
        RACECOURSE.contains(x, y) -> Terrain.TRACK
        POND.contains(x, y) -> Terrain.WATER
        PATHS.any { it.contains(x, y) } -> Terrain.PATH
        HILL.contains(x, y) -> Terrain.HILL
        FLOWER_BEDS.any { it.contains(x, y) } -> Terrain.FLOWERS
        else -> Terrain.GRASS
    }

    /** The big map with [yardPlot] (a 24×24 plot) moved into the yard. */
    fun expand(yardPlot: Plot): Plot = Plot(
        size = SIZE,
        objects = yardPlot.objects.map { it.copy(x = it.x + YARD.x, y = it.y + YARD.y) } + wildObjects(),
        rooms = yardPlot.rooms.map { it.copy(x = it.x + YARD.x, y = it.y + YARD.y) },
        yard = YARD,
        surroundings = true,
    )

    /** Worlds saved before the map grew move into the yard: the plot, its rooms, and everyone standing on it. */
    fun upgrade(world: World): World {
        if (world.camp.plot.surroundings) return topUp(world)
        return world.copy(
            camp = world.camp.copy(plot = expand(world.camp.plot)),
            players = world.players.mapValues { (_, p) -> p.copy(tileX = p.tileX + YARD.x, tileY = p.tileY + YARD.y) },
        )
    }

    /** Adds anything new out in the world since this one was saved, wherever its spot is still free. */
    private fun topUp(world: World): World {
        val plot = world.camp.plot
        val ids = plot.objects.map { it.id }.toSet()
        val missing = wildObjects().filter { obj ->
            obj.id !in ids && obj.tiles().all { (x, y) -> plot.objectAt(x, y) == null && world.players.values.none { it.tile == x to y } }
        }
        if (missing.isEmpty()) return world
        return world.copy(camp = world.camp.copy(plot = plot.copy(objects = plot.objects + missing)))
    }

    /** Everything outside the yard. Fixed positions, so every world's surroundings match. */
    private fun wildObjects(): List<PlotObject> {
        val placed = mutableListOf<PlotObject>()
        val taken = mutableSetOf<Pair<Int, Int>>()
        val counts = mutableMapOf<ObjectKind, Int>()

        fun place(kind: ObjectKind, x: Int, y: Int) {
            val spot = PlotObject("", kind, x, y)
            val tiles = spot.tiles()
            val fits = tiles.all { (tx, ty) ->
                tx in 0 until SIZE && ty in 0 until SIZE && !YARD.contains(tx, ty) &&
                    terrainAt(tx, ty).let { it == Terrain.GRASS || it == Terrain.HILL } && (tx to ty) !in taken
            }
            if (!fits) return
            val n = (counts[kind] ?: 0) + 1
            counts[kind] = n
            placed += spot.copy(id = "wild-${kind.name.lowercase()}-$n")
            taken += tiles
        }

        place(ObjectKind.BENCH, 31, 6)
        place(ObjectKind.RACE_SIGN, 46, 30)
        place(ObjectKind.TRAILHEAD, 32, 45)

        // The forest around the trail, in loose rows with gaps to walk between.
        for (x in 2..42 step 3) for (y in 46..62 step 3) place(ObjectKind.TREE, x + (x * 7 + y * 3) % 2, y)
        // A scattering of trees in the north-west and north-east.
        for (x in 2..18 step 4) for (y in 2..18 step 4) place(ObjectKind.TREE, x + (y / 4) % 2, y)
        for (x in 38..44 step 3) for (y in 2..16 step 5) place(ObjectKind.TREE, x, y)

        listOf(14 to 24, 15 to 31, 3 to 31, 25 to 8, 38 to 7, 17 to 41, 45 to 50).forEach { (x, y) -> place(ObjectKind.ROCK, x, y) }
        listOf(16 to 22, 2 to 40, 24 to 12, 40 to 18, 45 to 24, 18 to 36, 13 to 33).forEach { (x, y) -> place(ObjectKind.BUSH, x, y) }
        return placed
    }
}
