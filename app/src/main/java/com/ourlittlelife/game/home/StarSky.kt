package com.ourlittlelife.game.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.R
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.theme.OllType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The night sky is world art, not interface: its colours live here rather than in the theme. */
private val SkyTop = Color(0xFF070B1E)
private val SkyBottom = Color(0xFF1C2452)
private val Hill = Color(0xFF0D1712)
private val StarLight = Color(0xFFFFF6D8)
private val SkyText = Color(0xFFEEF1EA)
private val SkyTextMuted = Color(0xFFB7C0D8)

private data class Star(val x: Float, val y: Float, val radius: Float, val speed: Float, val phase: Float)

/**
 * The full-screen sky after "Look at the stars": twinkling stars, the odd shooting star,
 * and the characters sitting on a hill. Closing it (or pressing back) ends the hour.
 */
@Composable
fun StarSky(
    people: List<List<Sprite>>,
    companionName: String?,
    socialBonus: Int,
    onClose: () -> Unit,
    scene3d: (@Composable (Modifier) -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    val stars = remember {
        val random = Random(7)
        List(150) { Star(random.nextFloat(), random.nextFloat() * 0.72f, 0.6f + random.nextFloat() * 1.6f, 0.5f + random.nextFloat() * 1.5f, random.nextFloat() * 6.28f) }
    }
    val sheet = ImageBitmap.imageResource(R.drawable.sheet_characters)

    // One clock drives the twinkle and the shooting star; with "Remove animations" on, the sky simply holds still.
    val clock = rememberInfiniteTransition(label = "sky")
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyBottom)))
            // Swallow taps so the map underneath can't be poked while the sky is open.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (scene3d != null) {
            scene3d(Modifier.fillMaxSize().semantics { contentDescription = "The two of you lying on a blanket under the stars" })
        } else Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A night sky full of stars" },
        ) {
            stars.forEach { star ->
                val twinkle = 0.35f + 0.65f * abs(sin(time * star.speed + star.phase))
                drawCircle(
                    color = StarLight.copy(alpha = twinkle),
                    radius = star.radius.dp.toPx(),
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }

            // A shooting star crosses the top of the sky during the first sixth of each cycle.
            val streak = time / (2 * PI).toFloat() * 6f
            if (streak < 1f) {
                val start = Offset(size.width * (0.15f + 0.6f * streak), size.height * (0.08f + 0.18f * streak))
                drawLine(
                    color = StarLight.copy(alpha = 1f - streak),
                    start = start,
                    end = start - Offset(60.dp.toPx(), 18.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // The hill, and everyone sitting on it, side by side.
            val hillTop = size.height * 0.80f
            val hill = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, hillTop + 40.dp.toPx())
                quadraticBezierTo(size.width / 2, hillTop - 30.dp.toPx(), size.width, hillTop + 40.dp.toPx())
                lineTo(size.width, size.height)
                close()
            }
            drawPath(hill, Hill)

            val scale = (56.dp.toPx() / 16).roundToInt().coerceAtLeast(2)
            val spriteSize = 16 * scale
            val totalWidth = people.size * spriteSize + (people.size - 1) * 8.dp.toPx()
            var x = (size.width - totalWidth) / 2
            people.forEach { layers ->
                layers.forEach { layer ->
                    drawImage(
                        image = sheet,
                        srcOffset = IntOffset(layer.col * Sprites.STRIDE, layer.row * Sprites.STRIDE),
                        srcSize = IntSize(Sprites.TILE, Sprites.TILE),
                        dstOffset = IntOffset(x.roundToInt(), (hillTop - spriteSize + 6.dp.toPx()).roundToInt()),
                        dstSize = IntSize(spriteSize, spriteSize),
                        filterQuality = FilterQuality.None,
                    )
                }
                x += spriteSize + 8.dp.toPx()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Stargazing", style = OllType.display, color = SkyText, modifier = Modifier.semantics { heading() })
            Text(
                companionName?.let { if (socialBonus > 0) "With $it" else "$it is too far away to join you" } ?: "Just you and the sky",
                style = OllType.body,
                color = SkyTextMuted,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            PrimaryButton(
                text = "Head back",
                detail = "1h · +20 Fun" + if (socialBonus > 0) " · +$socialBonus Social" else "",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
