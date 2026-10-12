package com.ourlittlelife.game.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import com.ourlittlelife.game.R
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.Furniture
import com.ourlittlelife.rules.Interiors
import com.ourlittlelife.rules.Room
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Inside a room: its floor filling the space, walls round the edge with a gap for the door,
 * the furniture, and whoever is in here. Tap furniture to use it, or the floor to walk there.
 */
@Composable
fun RoomCanvas(
    room: Room,
    characters: List<PlacedCharacter>,
    selected: Furniture?,
    ghost: Ghost?,
    onTapTile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OllTheme.colors
    val outdoor = ImageBitmap.imageResource(R.drawable.sheet_outdoor)
    val indoor = ImageBitmap.imageResource(R.drawable.sheet_indoor)
    val people = ImageBitmap.imageResource(R.drawable.sheet_characters)
    val textMeasurer = rememberTextMeasurer()
    val columns = Interiors.width(room.kind)
    val rows = Interiors.height(room.kind)
    val door = Interiors.door(room.kind)

    val glide = characters.map { person ->
        key(person.name) {
            animateOffsetAsState(
                targetValue = Offset(person.x.toFloat(), person.y.toFloat()),
                animationSpec = tween(durationMillis = WALK_STEP_MILLIS.toInt(), easing = LinearEasing),
                label = "inside-${person.name}",
            ).value
        }
    }

    // The whole room always fits, at the biggest whole-number scale that leaves room for the walls.
    fun layout(width: Float, height: Float): Pair<Float, Offset> {
        val wall = 12.dp.value
        val scale = floor(minOf((width - wall * 4) / (columns * Sprites.TILE), (height - wall * 4) / (rows * Sprites.TILE))).coerceAtLeast(1f)
        val tile = scale * Sprites.TILE
        return tile to Offset((width - columns * tile) / 2, (height - rows * tile) / 2)
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Inside the ${room.kind.label.lowercase()}. Tap furniture to use it, or the floor to walk." }
            .pointerInput(room) {
                detectTapGestures { tap ->
                    val (tile, origin) = layout(size.width.toFloat(), size.height.toFloat())
                    onTapTile(floor((tap.x - origin.x) / tile).toInt(), floor((tap.y - origin.y) / tile).toInt())
                }
            },
    ) {
        val (tile, origin) = layout(size.width, size.height)
        val tileInt = tile.roundToInt()

        fun DrawScope.spriteAt(sprite: Sprite, tx: Float, ty: Float) = drawImage(
            image = when (sprite.sheet) {
                Sheet.OUTDOOR -> outdoor
                Sheet.INDOOR -> indoor
                Sheet.CHARACTERS -> people
            },
            srcOffset = IntOffset(sprite.col * Sprites.STRIDE, sprite.row * Sprites.STRIDE),
            srcSize = IntSize(Sprites.TILE, Sprites.TILE),
            dstOffset = IntOffset((origin.x + tx * tile).roundToInt(), (origin.y + ty * tile).roundToInt()),
            dstSize = IntSize(tileInt, tileInt),
            filterQuality = FilterQuality.None,
        )

        drawRect(colors.surfaceSunken)

        // Walls: a thick wooden frame round the floor, open where the door is.
        val wall = 10.dp.toPx()
        drawRect(
            color = Sprites.wallColor,
            topLeft = Offset(origin.x - wall, origin.y - wall),
            size = Size(columns * tile + wall * 2, rows * tile + wall * 2),
        )
        for (tx in 0 until columns) for (ty in 0 until rows) spriteAt(Sprites.floor, tx.toFloat(), ty.toFloat())
        drawRect(
            color = Sprites.floorDoorway,
            topLeft = Offset(origin.x + door.first * tile, origin.y + rows * tile),
            size = Size(tile, wall),
        )

        room.furniture.sortedBy { it.y + it.kind.height }.forEach { piece ->
            Sprites.forFurniture(piece.kind).forEach { part -> spriteAt(part.sprite, (piece.x + part.dx).toFloat(), (piece.y + part.dy).toFloat()) }
        }

        characters.forEachIndexed { i, person ->
            val at = glide[i]
            person.layers.forEach { layer -> spriteAt(layer, at.x, at.y) }
            val label = textMeasurer.measure(person.name, OllType.caption.copy(color = colors.onFill))
            val pad = 4.dp.toPx()
            val tagLeft = origin.x + (at.x + 0.5f) * tile - label.size.width / 2f - pad
            val tagTop = origin.y + (at.y + 1) * tile + 2.dp.toPx()
            drawRoundRect(
                color = person.tag,
                topLeft = Offset(tagLeft, tagTop),
                size = Size(label.size.width + pad * 2, label.size.height.toFloat()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawText(label, topLeft = Offset(tagLeft + pad, tagTop))
        }

        // Where the piece in hand would go: green-tinted if it fits, berry-tinted if not, with a dashed edge.
        ghost?.let { (area, fits) ->
            val tint = if (fits) colors.leaf else colors.berry
            val topLeft = Offset(origin.x + area.x * tile, origin.y + area.y * tile)
            val areaSize = Size(area.width * tile, area.height * tile)
            drawRect(color = tint.copy(alpha = 0.35f), topLeft = topLeft, size = areaSize)
            drawRect(
                color = tint,
                topLeft = topLeft,
                size = areaSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                ),
            )
        }

        selected?.let { piece ->
            val inset = 2.dp.toPx()
            drawRoundRect(
                color = colors.ink,
                topLeft = Offset(origin.x + piece.x * tile - inset, origin.y + piece.y * tile - inset),
                size = Size(piece.kind.width * tile + inset * 2, piece.kind.height * tile + inset * 2),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
