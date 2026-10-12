package com.ourlittlelife.game.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.R
import com.ourlittlelife.game.ui.components.NeedBar
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.RaceOutcome
import com.ourlittlelife.rules.Races
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** The racecourse is world art, not interface: its colours live here rather than in the theme. */
private val Infield = Color(0xFF6FA83A)
private val Track = Color(0xFFB07A4F)
private val Lines = Color(0xFFF4EBD9)

// Tuning: alternate taps speed you up, the same foot twice makes you stumble, and every tap costs stamina.
private const val LAP = 100f
// Roughly 21s at an easy four taps a second, 15s flat out; tired runners lose a few seconds.
private const val TOP_SPEED = 11f
private const val STRIDE = 1.0f
private const val TIRED_STRIDE = 0.3f
private const val DRAG = 0.8f
private const val TAP_COST = 2.2f
private const val RECOVERY = 4f

private enum class Phase { READY, COUNTDOWN, RUNNING, FINISHED }
private enum class Foot { LEFT, RIGHT }

/**
 * One lap of the racecourse. Tap Left and Right in turn to run; your partner's best lap runs
 * alongside as a ghost. Leaving before the finish costs nothing.
 */
@Composable
fun RaceTrack(
    runner: List<Sprite>,
    ghost: List<Sprite>?,
    ghostName: String?,
    ghostMillis: Long?,
    bestMillis: Long?,
    stamina: Int,
    outcome: RaceOutcome?,
    onFinish: (Long) -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = OllTheme.colors
    val sheet = ImageBitmap.imageResource(R.drawable.sheet_characters)
    BackHandler(onBack = onClose)

    var phase by remember { mutableStateOf(Phase.READY) }
    var countdown by remember { mutableIntStateOf(3) }
    var progress by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    var energy by remember { mutableFloatStateOf(stamina.toFloat()) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var lastFoot by remember { mutableStateOf<Foot?>(null) }
    var stumbledAt by remember { mutableLongStateOf(-10_000L) }

    fun reset() {
        progress = 0f; speed = 0f; energy = stamina.toFloat(); elapsed = 0L; lastFoot = null; stumbledAt = -10_000L
        countdown = 3
        phase = Phase.COUNTDOWN
    }

    fun step(foot: Foot) {
        if (phase != Phase.RUNNING) return
        if (foot == lastFoot) {
            speed *= 0.5f
            stumbledAt = elapsed
        } else {
            // A full stride needs the stamina to pay for it; otherwise it's a weary shuffle.
            if (energy >= TAP_COST) {
                speed = (speed + STRIDE).coerceAtMost(TOP_SPEED)
                energy -= TAP_COST
            } else {
                speed = (speed + TIRED_STRIDE).coerceAtMost(TOP_SPEED)
            }
        }
        lastFoot = foot
    }

    LaunchedEffect(phase) {
        when (phase) {
            Phase.COUNTDOWN -> {
                while (countdown > 0) {
                    kotlinx.coroutines.delay(700)
                    countdown--
                }
                phase = Phase.RUNNING
            }
            Phase.RUNNING -> {
                var last = withFrameMillis { it }
                while (phase == Phase.RUNNING) {
                    val now = withFrameMillis { it }
                    val dt = (now - last).coerceAtMost(100) / 1000f
                    last = now
                    elapsed += (dt * 1000).roundToInt()
                    speed *= exp(-DRAG * dt)
                    energy = (energy + RECOVERY * dt).coerceAtMost(stamina.toFloat())
                    progress += speed * dt
                    if (progress >= LAP) {
                        progress = LAP
                        phase = Phase.FINISHED
                        onFinish(elapsed)
                    }
                }
            }
            else -> Unit
        }
    }

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Racecourse", style = OllType.display, color = colors.ink, modifier = Modifier.semantics { heading() })
                Text(formatLap(elapsed), style = OllType.stat, color = colors.ink)
            }
            Text(
                listOfNotNull(
                    bestMillis?.let { "Your best ${formatLap(it)}" },
                    if (ghostName != null && ghostMillis != null) "$ghostName's best ${formatLap(ghostMillis)}" else null,
                ).joinToString(" · ").ifEmpty { "No laps yet. Set the first time." },
                style = OllType.body,
                color = colors.inkMuted,
            )

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = "The track. You've run ${(progress / LAP * 100).roundToInt()} percent of the lap." },
            ) {
                val pad = 12.dp.toPx()
                val lane = 36.dp.toPx()
                val radius = (size.width / 2 - pad - lane / 2).coerceAtMost(size.height / 3)
                val straight = (size.height - 2 * pad - lane - 2 * radius).coerceAtLeast(0f)
                val centre = Offset(size.width / 2, size.height / 2)
                val perimeter = 2 * straight + 2 * PI.toFloat() * radius

                // A stadium: dirt lane round a grass infield, with a white start and finish line.
                drawRoundRect(
                    color = Track,
                    topLeft = Offset(centre.x - radius - lane / 2, centre.y - straight / 2 - radius - lane / 2),
                    size = Size(2 * radius + lane, straight + 2 * radius + lane),
                    cornerRadius = CornerRadius(radius + lane / 2),
                )
                drawRoundRect(
                    color = Infield,
                    topLeft = Offset(centre.x - radius + lane / 2, centre.y - straight / 2 - radius + lane / 2),
                    size = Size(2 * radius - lane, straight + 2 * radius - lane),
                    cornerRadius = CornerRadius((radius - lane / 2).coerceAtLeast(0f)),
                )
                drawRoundRect(
                    color = Lines,
                    topLeft = Offset(centre.x - radius, centre.y - straight / 2 - radius),
                    size = Size(2 * radius, straight + 2 * radius),
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawRect(Lines, topLeft = Offset(centre.x + radius - lane / 2, centre.y + straight / 2 - 2.dp.toPx()), size = Size(lane, 4.dp.toPx()))

                // Where on the lane a runner is, a fraction of the way round, starting up the right-hand straight.
                fun at(fraction: Float): Offset {
                    var d = (fraction.coerceIn(0f, 1f) * perimeter)
                    if (d <= straight) return Offset(centre.x + radius, centre.y + straight / 2 - d)
                    d -= straight
                    val arc = PI.toFloat() * radius
                    if (d <= arc) {
                        val angle = d / radius
                        return Offset(centre.x + radius * cos(angle), centre.y - straight / 2 - radius * sin(angle))
                    }
                    d -= arc
                    if (d <= straight) return Offset(centre.x - radius, centre.y - straight / 2 + d)
                    d -= straight
                    val angle = PI.toFloat() + d / radius
                    return Offset(centre.x + radius * cos(angle), centre.y + straight / 2 - radius * sin(angle))
                }

                val spriteSize = ((32.dp.toPx() / 16).roundToInt().coerceAtLeast(2)) * 16
                fun drawRunner(layers: List<Sprite>, fraction: Float, alpha: Float) {
                    val spot = at(fraction)
                    layers.forEach { layer ->
                        drawImage(
                            image = sheet,
                            srcOffset = IntOffset(layer.col * Sprites.STRIDE, layer.row * Sprites.STRIDE),
                            srcSize = IntSize(Sprites.TILE, Sprites.TILE),
                            dstOffset = IntOffset((spot.x - spriteSize / 2).roundToInt(), (spot.y - spriteSize / 2).roundToInt()),
                            dstSize = IntSize(spriteSize, spriteSize),
                            alpha = alpha,
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
                if (ghost != null && ghostMillis != null) {
                    drawRunner(ghost, if (phase == Phase.READY || phase == Phase.COUNTDOWN) 0f else elapsed.toFloat() / ghostMillis, 0.45f)
                }
                drawRunner(runner, progress / LAP, 1f)
            }

            when (phase) {
                Phase.READY -> {
                    Text(
                        "Tap Left and Right in turn to run. The same foot twice and you stumble. " +
                            (ghostName?.takeIf { ghostMillis != null }?.let { "The faded runner is $it's best lap." } ?: ""),
                        style = OllType.body,
                        color = colors.ink,
                    )
                    PrimaryButton(text = "On your marks", detail = "30m · +15 Fun", onClick = ::reset, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = "Head back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                Phase.COUNTDOWN, Phase.RUNNING -> {
                    val stumbling = elapsed - stumbledAt < 600
                    Text(
                        when {
                            phase == Phase.COUNTDOWN -> if (countdown > 0) "$countdown…" else "Go"
                            stumbling -> "Stumble. Left, then right."
                            energy < TAP_COST -> "Out of breath. Keep going, slower."
                            else -> "Run."
                        },
                        style = OllType.title,
                        color = colors.ink,
                    )
                    NeedBar(label = "Stamina", value = (energy / stamina * 100).roundToInt())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FootPad("Left", onStep = { step(Foot.LEFT) }, modifier = Modifier.weight(1f))
                        FootPad("Right", onStep = { step(Foot.RIGHT) }, modifier = Modifier.weight(1f))
                    }
                    SecondaryButton(text = "Stop and head back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                }
                Phase.FINISHED -> {
                    Text(resultLine(outcome, elapsed, ghostName), style = OllType.title, color = colors.ink)
                    PrimaryButton(text = "Head back", onClick = onClose, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = "Run another lap", onClick = { onAgain(); phase = Phase.READY }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** A big pad that reacts the moment it's pressed, so fast tapping keeps up. */
@Composable
private fun FootPad(label: String, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.honey)
            .pointerInput(Unit) { detectTapGestures(onPress = { onStep() }) }
            .semantics {
                role = Role.Button
                onClick(label = label) { onStep(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = OllType.button, color = colors.onFill)
    }
}

private fun resultLine(outcome: RaceOutcome?, millis: Long, ghostName: String?): String {
    val time = formatLap(millis)
    if (outcome == null) return "$time. That one didn't count."
    val best = if (outcome.personalBest) " New best, +${Races.BEST_PRIZE} coins." else outcome.previousBest?.let { " Your best is ${formatLap(it)}." } ?: ""
    val theirs = outcome.partnerBest
    val race = when {
        ghostName == null || theirs == null -> ""
        outcome.beatPartner -> " You beat $ghostName's best."
        else -> " ${formatLap(millis - theirs)} behind $ghostName."
    }
    return "$time.$best$race"
}

/** "23.4s" */
fun formatLap(millis: Long): String = "${millis / 1000}.${(millis % 1000) / 100}s"
