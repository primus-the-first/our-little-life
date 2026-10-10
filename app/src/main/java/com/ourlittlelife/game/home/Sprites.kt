package com.ourlittlelife.game.home

import androidx.compose.ui.graphics.Color
import com.ourlittlelife.rules.Area
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.Terrain
import com.ourlittlelife.rules.WorldMap

/** The three Kenney sheets (CC0). Every tile is 16×16 px with a 1 px gap between tiles. */
enum class Sheet { OUTDOOR, INDOOR, CHARACTERS }

data class Sprite(val sheet: Sheet, val col: Int, val row: Int)

/** A sprite drawn [dx], [dy] tiles from an object's top-left tile. */
data class SpritePart(val dx: Int, val dy: Int, val sprite: Sprite)

object Sprites {
    const val TILE = 16
    const val STRIDE = 17

    val grass = Sprite(Sheet.OUTDOOR, 5, 0)
    val dirt = Sprite(Sheet.OUTDOOR, 6, 0)
    val floor = Sprite(Sheet.OUTDOOR, 8, 3)

    /** Wooden walls around rooms: part of the world art, matched to the Kenney planks, not a UI colour. */
    val wallColor = Color(0xFF8A5A3C)

    fun forKind(kind: ObjectKind): List<SpritePart> = when (kind) {
        ObjectKind.TENT -> listOf(
            SpritePart(0, 0, Sprite(Sheet.OUTDOOR, 46, 10)),
            SpritePart(1, 0, Sprite(Sheet.OUTDOOR, 47, 10)),
            SpritePart(0, 1, Sprite(Sheet.OUTDOOR, 46, 11)),
            SpritePart(1, 1, Sprite(Sheet.OUTDOOR, 47, 11)),
        )
        ObjectKind.CAMPFIRE -> single(Sprite(Sheet.OUTDOOR, 14, 8))
        ObjectKind.WASHBASIN -> single(Sprite(Sheet.INDOOR, 8, 12))
        ObjectKind.WORKBENCH -> single(Sprite(Sheet.OUTDOOR, 19, 6))
        ObjectKind.CHEST -> single(Sprite(Sheet.OUTDOOR, 38, 10))
        ObjectKind.SIGNPOST -> single(Sprite(Sheet.OUTDOOR, 19, 0))
        ObjectKind.TREE -> single(Sprite(Sheet.OUTDOOR, 13, 11))
        ObjectKind.ROCK -> single(Sprite(Sheet.OUTDOOR, 8, 18))
        ObjectKind.BUSH -> single(Sprite(Sheet.OUTDOOR, 24, 9))
        ObjectKind.BENCH -> single(Sprite(Sheet.OUTDOOR, 23, 4))
        ObjectKind.RACE_SIGN -> single(Sprite(Sheet.OUTDOOR, 20, 0))
        ObjectKind.TRAILHEAD -> single(Sprite(Sheet.OUTDOOR, 21, 0))
    }

    private val path = Sprite(Sheet.OUTDOOR, 8, 0)
    private val flowers = listOf(Sprite(Sheet.OUTDOOR, 0, 6), Sprite(Sheet.OUTDOOR, 0, 9), Sprite(Sheet.OUTDOOR, 0, 12))
    private val fenceAcross = Sprite(Sheet.OUTDOOR, 47, 23)
    private val fenceDown = Sprite(Sheet.OUTDOOR, 45, 23)

    /** The ground at a tile, bottom layer first. Inside the yard's camp it's bare dirt. */
    fun ground(plot: Plot, x: Int, y: Int): List<Sprite> = when (plot.terrainAt(x, y)) {
        Terrain.GRASS -> {
            val cx = x - plot.yard.x
            val cy = y - plot.yard.y
            listOf(if (cx in 8..15 && cy in 8..13) dirt else grass)
        }
        Terrain.PATH -> listOf(path)
        Terrain.FLOWERS -> listOf(flowers[Math.floorMod(x * 5 + y * 3, flowers.size)])
        Terrain.HILL -> listOf(grass, nineSlice(WorldMap.HILL, x, y, col = 2, row = 15))
        Terrain.WATER -> listOf(nineSlice(WorldMap.POND, x, y, col = 2, row = 0))
        Terrain.TRACK -> racecourse(x, y)
    }

    /** A 3×3 block of sheet tiles stretched over [area]: corners, edges, and the middle repeated. */
    private fun nineSlice(area: Area, x: Int, y: Int, col: Int, row: Int): Sprite {
        val dx = when (x) { area.x -> 0; area.x + area.width - 1 -> 2; else -> 1 }
        val dy = when (y) { area.y -> 0; area.y + area.height - 1 -> 2; else -> 1 }
        return Sprite(Sheet.OUTDOOR, col + dx, row + dy)
    }

    /** Fenced all round, with a dirt track two tiles in from the fence. */
    private fun racecourse(x: Int, y: Int): List<Sprite> {
        val area = WorldMap.RACECOURSE
        val fromEdge = minOf(x - area.x, area.x + area.width - 1 - x, y - area.y, area.y + area.height - 1 - y)
        return when (fromEdge) {
            0 -> listOf(grass, if (y == area.y || y == area.y + area.height - 1) fenceAcross else fenceDown)
            2, 3 -> listOf(dirt)
            else -> listOf(grass)
        }
    }

    /** Character layers, drawn in order: body, trousers, shoes, shirt, hair. */
    val jo = listOf(
        Sprite(Sheet.CHARACTERS, 0, 1),
        Sprite(Sheet.CHARACTERS, 3, 0),
        Sprite(Sheet.CHARACTERS, 4, 1),
        Sprite(Sheet.CHARACTERS, 6, 0),
        Sprite(Sheet.CHARACTERS, 19, 0),
    )

    val maya = listOf(
        Sprite(Sheet.CHARACTERS, 0, 2),
        Sprite(Sheet.CHARACTERS, 3, 4),
        Sprite(Sheet.CHARACTERS, 4, 0),
        Sprite(Sheet.CHARACTERS, 10, 0),
        Sprite(Sheet.CHARACTERS, 24, 4),
    )

    private fun single(sprite: Sprite) = listOf(SpritePart(0, 0, sprite))
}
