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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.FishKind
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** The pond is world art, not interface: its colours live here rather than in the theme. */
private val Water = Color(0xFF4FA3C7)
private val WaterDeep = Color(0xFF3B86A8)
private val Ripple = Color(0xFFD7EEF6)
private val BobberRed = Color(0xFFD9534F)
private val BobberWhite = Color(0xFFF7F3EA)

private enum class Cast { READY, WAITING, BITE, REELING, RESULT }

/** How long you have to react to a bite, and how wide the green zone is on the reel. */
private const val BITE_WINDOW_MILLIS = 900L
private const val ZONE_HALF_WIDTH = 0.12f
private const val NEEDLE_PERIOD_MILLIS = 1400f

/**
 * Fishing at the pond: cast, wait for the bob, tap when it bites, then stop the needle inside the
 * green zone. The cleaner the stop, the better the fish. Every cast takes a quarter of an hour.
 */
@Composable
fun FishingPond(
    log: Map<FishKind, Int>,
    onLand: (quality: Float) -> FishKind?,
    onMiss: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = OllTheme.colors
    BackHandler(onBack = onClose)

    var state by remember { mutableStateOf(Cast.READY) }
    var result by remember { mutableStateOf("") }
    var needle by remember { mutableFloatStateOf(0f) }
    var zone by remember { mutableFloatStateOf(0.5f) }

    fun missed(why: String) {
        onMiss()
        result = why
        state = Cast.RESULT
    }

    // Waiting for a bite, then a short window to react.
    LaunchedEffect(state) {
        when (state) {
            Cast.WAITING -> {
                delay(Random.nextLong(1_500, 5_000))
                state = Cast.BITE
            }
            Cast.BITE -> {
                delay(BITE_WINDOW_MILLIS)
                if (state == Cast.BITE) missed("It got away. Tap as soon as the float dips.")
            }
            Cast.REELING -> {
                zone = Random.nextFloat() * 0.5f + 0.25f
                val start = withFrameMillis { it }
                while (state == Cast.REELING) {
                    val t = withFrameMillis { it } - start
                    needle = (sin(2 * PI * t / NEEDLE_PERIOD_MILLIS).toFloat() + 1) / 2
                }
            }
            else -> Unit
        }
    }

    fun press() {
        when (state) {
            Cast.READY, Cast.RESULT -> state = Cast.WAITING
            Cast.WAITING -> missed("Too soon. The fish swam off.")
            Cast.BITE -> state = Cast.REELING
            Cast.REELING -> {
                val off = abs(needle - zone)
                if (off <= ZONE_HALF_WIDTH) {
                    val kind = onLand(1 - off / ZONE_HALF_WIDTH)
                    result = kind?.let { "You caught a ${it.label.lowercase()}." + if (it.coins > 0) " Worth ${it.coins} coins." else "" } ?: "That one didn't count."
                    state = Cast.RESULT
                } else {
                    missed("It slipped off the hook.")
                }
            }
        }
    }

    // The float bobs gently while you wait, and plunges when something bites.
    val bob = rememberInfiniteTransition(label = "bob")
    val wobble by bob.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "wobble")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("The pond", style = OllType.display, color = colors.ink, modifier = Modifier.semantics { heading() })
            Text(
                FishKind.entries.joinToString(" · ") { kind -> log[kind]?.let { "${kind.label} $it" } ?: "${kind.label} ?" },
                style = OllType.body,
                color = colors.inkMuted,
            )

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .semantics {
                        contentDescription = when (state) {
                            Cast.BITE -> "The float has dipped. Something's biting."
                            Cast.WAITING -> "The float bobs on the water."
                            else -> "The pond."
                        }
                    },
            ) {
                drawRect(Water)
                for (i in 0 until 6) {
                    val y = size.height * (0.15f + i * 0.15f)
                    drawLine(WaterDeep, Offset(size.width * 0.1f, y), Offset(size.width * 0.45f, y), strokeWidth = 2.dp.toPx())
                    drawLine(WaterDeep, Offset(size.width * 0.6f, y + 18.dp.toPx()), Offset(size.width * 0.9f, y + 18.dp.toPx()), strokeWidth = 2.dp.toPx())
                }
                if (state == Cast.WAITING || state == Cast.BITE || state == Cast.REELING) {
                    val centre = Offset(size.width / 2, size.height / 2)
                    val dip = when (state) {
                        Cast.BITE -> 10.dp.toPx()
                        Cast.REELING -> 4.dp.toPx()
                        else -> (wobble - 0.5f) * 4.dp.toPx()
                    }
                    if (state == Cast.BITE) {
                        drawCircle(Ripple, radius = 34.dp.toPx(), center = centre, style = Stroke(width = 2.dp.toPx()))
                        drawCircle(Ripple, radius = 22.dp.toPx(), center = centre, style = Stroke(width = 2.dp.toPx()))
                    }
                    drawCircle(BobberWhite, radius = 9.dp.toPx(), center = centre + Offset(0f, dip))
                    drawCircle(BobberRed, radius = 9.dp.toPx(), center = centre + Offset(0f, dip - 5.dp.toPx()))
                }
            }

            Text(
                when (state) {
                    Cast.READY -> "Cast your line, wait for the float to dip, then reel in."
                    Cast.WAITING -> "Waiting for a bite…"
                    Cast.BITE -> "Bite. Reel in."
                    Cast.REELING -> "Stop the needle in the green."
                    Cast.RESULT -> result
                },
                style = OllType.title,
                color = colors.ink,
            )

            if (state == Cast.REELING) {
                ReelBar(needle = needle, zone = zone)
            }

            PrimaryButton(
                text = when (state) {
                    Cast.READY -> "Cast"
                    Cast.WAITING, Cast.BITE -> "Reel in"
                    Cast.REELING -> "Now"
                    Cast.RESULT -> "Cast again"
                },
                detail = if (state == Cast.READY || state == Cast.RESULT) "15m · +6 Fun" else null,
                onClick = ::press,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(text = "Head back", onClick = onClose, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The reel: a track with a green zone and a needle sweeping back and forth. */
@Composable
private fun ReelBar(needle: Float, zone: Float) {
    val colors = OllTheme.colors
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceSunken)
            .semantics { contentDescription = if (abs(needle - zone) <= ZONE_HALF_WIDTH) "The needle is in the green" else "The needle is outside the green" },
    ) {
        val width = maxWidth
        Box(
            Modifier
                .offset(x = width * (zone - ZONE_HALF_WIDTH))
                .width(width * ZONE_HALF_WIDTH * 2)
                .fillMaxHeight()
                .background(colors.leaf),
        )
        Box(
            Modifier
                .offset(x = width * needle - 2.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(colors.ink),
        )
    }
}
