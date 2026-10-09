package com.ourlittlelife.game.home

import com.ourlittlelife.rules.ObjectKind

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
