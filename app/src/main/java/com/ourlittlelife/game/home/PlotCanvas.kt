package com.ourlittlelife.game.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.R
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.PlotObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** A character standing on a tile, with the colour of its name tag. */
data class PlacedCharacter(val name: String, val layers: List<Sprite>, val x: Int, val y: Int, val tag: Color)

/** A rectangle of tiles: what's selected, or where a room would go. */
data class TileArea(val x: Int, val y: Int, val width: Int, val height: Int)

/** A see-through room outline shown before building; [fits] says whether it can go there. */
data class Ghost(val area: TileArea, val fits: Boolean)

/** The tiles under the camp, drawn as bare dirt. */
private val campArea = 8..15 to 8..13

/**
 * The plot in pixel art. Pinch to zoom, drag to pan, tap a tile to select what stands there.
 * Sprites are always scaled by whole numbers so pixels stay square.
 */
@Composable
fun PlotCanvas(
    plot: Plot,
    characters: List<PlacedCharacter>,
    selected: TileArea?,
    ghost: Ghost?,
    focusTile: Pair<Int, Int>,
    onTapTile: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OllTheme.colors
    val outdoor = ImageBitmap.imageResource(R.drawable.sheet_outdoor)
    val indoor = ImageBitmap.imageResource(R.drawable.sheet_indoor)
    val people = ImageBitmap.imageResource(R.drawable.sheet_characters)
    val textMeasurer = rememberTextMeasurer()

    // Zoom is measured in whole multiples of the 16 px source tile; start so a tile is a comfortable tap target.
    val density = LocalDensity.current
    val minScale = 3f
    val maxScale = 12f
    val startScale = with(density) { ceil(44.dp.toPx() / Sprites.TILE) }.coerceIn(minScale, maxScale)
    var scale by remember { mutableFloatStateOf(startScale) }

    // Null until the player drags: until then the view stays centred on [focusTile] at whatever size it has.
    var userPan by remember { mutableStateOf<Offset?>(null) }

    fun centred(width: Int, height: Int, tile: Float) = clampPan(
        Offset(width / 2f - (focusTile.first + 0.5f) * tile, height / 2f - (focusTile.second + 0.5f) * tile),
        tile, plot.size, width, height,
    )

    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Your plot. Tap something to see what you can do with it." }
            .pointerInput(plot) {
                detectTransformGestures { centroid, dragged, zoom, _ ->
                    val oldTile = tilePx(scale)
                    val current = userPan ?: centred(size.width, size.height, oldTile)
                    scale = (scale * zoom).coerceIn(minScale, maxScale)
                    val newTile = tilePx(scale)
                    userPan = clampPan(centroid - (centroid - current) * (newTile / oldTile) + dragged, newTile, plot.size, size.width, size.height)
                }
            }
            .pointerInput(plot) {
                detectTapGestures { tap ->
                    val tile = tilePx(scale)
                    val origin = userPan ?: centred(size.width, size.height, tile)
                    val tx = floor((tap.x - origin.x) / tile).toInt()
                    val ty = floor((tap.y - origin.y) / tile).toInt()
                    onTapTile(tx, ty)
                }
            },
    ) {
        val tile = tilePx(scale)
        val tileInt = tile.roundToInt()
        val origin = userPan ?: centred(size.width.roundToInt(), size.height.roundToInt(), tile)

        fun sheetFor(sprite: Sprite) = when (sprite.sheet) {
            Sheet.OUTDOOR -> outdoor
            Sheet.INDOOR -> indoor
            Sheet.CHARACTERS -> people
        }

        fun DrawScope.sprite(sprite: Sprite, tx: Int, ty: Int) = drawImage(
            image = sheetFor(sprite),
            srcOffset = IntOffset(sprite.col * Sprites.STRIDE, sprite.row * Sprites.STRIDE),
            srcSize = IntSize(Sprites.TILE, Sprites.TILE),
            dstOffset = IntOffset((origin.x + tx * tile).roundToInt(), (origin.y + ty * tile).roundToInt()),
            dstSize = IntSize(tileInt, tileInt),
            filterQuality = FilterQuality.None,
        )

        drawRect(colors.surfaceSunken)

        // Ground: only the tiles on screen.
        val firstX = floor(-origin.x / tile).toInt().coerceAtLeast(0)
        val lastX = ceil((size.width - origin.x) / tile).toInt().coerceAtMost(plot.size - 1)
        val firstY = floor(-origin.y / tile).toInt().coerceAtLeast(0)
        val lastY = ceil((size.height - origin.y) / tile).toInt().coerceAtMost(plot.size - 1)
        for (tx in firstX..lastX) for (ty in firstY..lastY) {
            val bare = tx in campArea.first && ty in campArea.second
            sprite(if (bare) Sprites.dirt else Sprites.grass, tx, ty)
        }

        // Rooms: a plank floor inside a wooden wall, with the room's name in the corner.
        plot.rooms.forEach { room ->
            room.tiles().forEach { (tx, ty) -> sprite(Sprites.floor, tx, ty) }
            val wall = 4.dp.toPx()
            drawRect(
                color = Sprites.wallColor,
                topLeft = Offset(origin.x + room.x * tile + wall / 2, origin.y + room.y * tile + wall / 2),
                size = Size(room.kind.width * tile - wall, room.kind.height * tile - wall),
                style = Stroke(width = wall),
            )
            // The name sits on a small tag so it reads clearly against the planks.
            val name = textMeasurer.measure(room.kind.label, OllType.caption.copy(color = colors.ink))
            val pad = 6.dp.toPx()
            val tagTopLeft = Offset(origin.x + room.x * tile + wall * 2, origin.y + room.y * tile + wall * 2)
            drawRoundRect(
                color = colors.surfaceRaised,
                topLeft = tagTopLeft,
                size = Size(name.size.width + pad * 2, name.size.height + pad),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawText(name, topLeft = tagTopLeft + Offset(pad, pad / 2))
        }

        // Objects back to front, so lower ones overlap higher ones.
        plot.objects.sortedBy { it.y + it.kind.height }.forEach { obj ->
            Sprites.forKind(obj.kind).forEach { part -> sprite(part.sprite, obj.x + part.dx, obj.y + part.dy) }
        }

        characters.forEach { person ->
            person.layers.forEach { layer -> sprite(layer, person.x, person.y) }
            val label = textMeasurer.measure(person.name, OllType.caption.copy(color = colors.onFill))
            val pad = 4.dp.toPx()
            val tagLeft = origin.x + (person.x + 0.5f) * tile - label.size.width / 2f - pad
            val tagTop = origin.y + (person.y + 1) * tile + 2.dp.toPx()
            drawRoundRect(
                color = person.tag,
                topLeft = Offset(tagLeft, tagTop),
                size = Size(label.size.width + pad * 2, label.size.height.toFloat()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawText(label, topLeft = Offset(tagLeft + pad, tagTop))
        }

        // Where a room would go: green-tinted if it fits, berry-tinted if not, always with a dashed edge.
        ghost?.let { (area, fits) ->
            val tint = if (fits) colors.leaf else colors.berry
            val topLeft = Offset(origin.x + area.x * tile, origin.y + area.y * tile)
            val areaSize = Size(area.width * tile, area.height * tile)
            drawRect(color = tint.copy(alpha = 0.35f), topLeft = topLeft, size = areaSize)
            drawRect(
                color = tint,
                topLeft = topLeft,
                size = areaSize,
                style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 8.dp.toPx()))),
            )
        }

        selected?.let { area ->
            val inset = 2.dp.toPx()
            drawRoundRect(
                color = colors.ink,
                topLeft = Offset(origin.x + area.x * tile - inset, origin.y + area.y * tile - inset),
                size = Size(area.width * tile + inset * 2, area.height * tile + inset * 2),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** Screen pixels per tile: a whole multiple of the 16 px source tile. */
private fun tilePx(scale: Float): Float = scale.roundToInt().coerceAtLeast(1) * Sprites.TILE.toFloat()

/** Keep at least half a screen of the plot in view, so it can't be dragged away and lost. */
private fun clampPan(pan: Offset, tile: Float, plotSize: Int, width: Int, height: Int): Offset {
    val plotPx = plotSize * tile
    val x = pan.x.coerceIn(width / 2f - plotPx, width / 2f)
    val y = pan.y.coerceIn(height / 2f - plotPx, height / 2f)
    return Offset(x, y)
}
