package com.ourlittlelife.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType

/**
 * Anything you can press that floats: a rounded card with the design system's hard 3dp shadow, which
 * goes flat and moves down when pressed, like a toy button. With no [onClick] it's just a raised card.
 */
@Composable
fun PopCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    color: Color = OllTheme.colors.surfaceRaised,
    radius: Dp = 12.dp,
    onClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = OllTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressable = onClick != null && enabled
    val raised = !(pressable && pressed) && enabled
    Box(
        modifier = modifier
            .offset(y = if (pressable && pressed) 3.dp else 0.dp)
            .drawBehind {
                if (raised) {
                    drawRoundRect(colors.popShadow, topLeft = Offset(0f, 3.dp.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()))
                }
            }
            .clip(RoundedCornerShape(radius))
            .background(if (enabled) color else colors.surfaceSunken)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interaction, ripple(), enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                } else Modifier,
            ),
    ) { content() }
}

/** One thing an action takes or gives: "30m", "+6 Wood", "-6 Energy". The words carry it; the icon helps. */
data class Fact(val text: String, val icon: ImageVector? = null)

/**
 * Something you can do, as a card: a round icon, what it is, and what it takes and gives. When it
 * can't be done, [problem] says why in place of the facts. [primary] makes it the sheet's peach action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    facts: List<Fact> = emptyList(),
    problem: String? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
    tint: Color = OllTheme.colors.leaf,
    onScreen: Boolean = false,
) {
    val colors = OllTheme.colors
    val onCard = if (primary && enabled) colors.onFill else colors.ink
    val quiet = if (primary && enabled) colors.onFill else colors.inkMuted
    PopCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        // Inside a sheet the card is a shade off the sheet; straight on a screen it's a raised surface.
        color = if (primary) colors.peach else if (onScreen) colors.surfaceRaised else colors.surface,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (!enabled) colors.line else if (primary) colors.surfaceRaised else tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (!enabled) colors.inkMuted else if (primary) colors.ink else colors.onFill, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = OllType.button, color = if (enabled) onCard else colors.inkMuted)
                if (problem != null) {
                    // Why not, in plain ink: berry is kept for low needs and deleting things.
                    Text(problem, style = OllType.caption, color = if (enabled) quiet else colors.ink)
                } else if (facts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        facts.forEach { fact ->
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                fact.icon?.let { Icon(it, contentDescription = null, tint = quiet, modifier = Modifier.size(14.dp)) }
                                Text(fact.text, style = OllType.caption, color = quiet)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small count with its icon, such as wood in the chest. Things with no icon show their name. */
@Composable
fun StatChip(value: String, label: String, icon: ImageVector?, modifier: Modifier = Modifier, fill: Color? = null) {
    val colors = OllTheme.colors
    val onChip = if (fill != null) colors.onFill else colors.ink
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fill ?: colors.surfaceSunken)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = onChip, modifier = Modifier.size(16.dp))
        else Text(label, style = OllType.caption, color = if (fill != null) colors.onFill else colors.inkMuted)
        Text(value, style = OllType.stat, color = onChip)
    }
}

/** A short status in a coloured pill, such as "Tourretta is up" on sky, with a dot for "here now". */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, fill: Color = OllTheme.colors.sky, live: Boolean = false) {
    val colors = OllTheme.colors
    val filled = fill != colors.surfaceSunken
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) Box(Modifier.size(8.dp).clip(CircleShape).background(colors.onFill))
        Text(text, style = OllType.caption, color = if (filled) colors.onFill else colors.ink, maxLines = 1)
    }
}

/** One need, small: its icon and number over a thin bar. Five of these sit across the top of the screen. */
@Composable
fun NeedMeter(label: String, value: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val low = value < 30
    val fill = when {
        value >= 60 -> colors.leaf
        !low -> colors.honey
        else -> colors.berry
    }
    val fraction by animateFloatAsState(value / 100f, tween(200), label = "meter")
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value of 100" + if (low) ", low" else "" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (low) colors.berry else colors.inkMuted, modifier = Modifier.size(16.dp))
            Text("$value", style = OllType.stat.copy(fontSize = OllType.caption.fontSize * 1.25f, lineHeight = OllType.caption.lineHeight), color = if (low) colors.berry else colors.ink)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(colors.surfaceSunken)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(50)).background(fill))
        }
    }
}

/** One destination in the bottom bar: an icon over a word. [emphasis] is the peach "do this now" state. */
@Composable
fun RowScope.BarItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasis: Boolean = false,
    selected: Boolean = false,
) {
    val colors = OllTheme.colors
    val fill = when {
        emphasis && enabled -> colors.peach
        selected -> colors.honey
        else -> Color.Transparent
    }
    val on = when {
        !enabled -> colors.inkMuted
        emphasis || selected -> colors.onFill
        else -> colors.ink
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = on, modifier = Modifier.size(24.dp))
        Text(label, style = OllType.caption.copy(fontWeight = OllType.button.fontWeight), color = on, maxLines = 1)
    }
}

/** A setting you turn on or off: what it is, a line about it, and a switch that also says "On" or "Off". */
@Composable
fun SettingSwitch(title: String, detail: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val knob by animateFloatAsState(if (checked) 1f else 0f, tween(150), label = "switch")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OllType.button, color = colors.ink)
            Text(detail, style = OllType.caption, color = colors.inkMuted)
        }
        Text(if (checked) "On" else "Off", style = OllType.caption, color = colors.inkMuted)
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) colors.leaf else colors.line)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = 18.dp * knob)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.onFill else colors.inkMuted),
            )
        }
    }
}

/** One of a few choices side by side (an outfit, a skin tone): the chosen one is filled and says so. */
@Composable
fun RowScope.ChoiceChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    val colors = OllTheme.colors
    Text(
        label,
        style = OllType.button,
        color = if (chosen) colors.onFill else colors.ink,
        maxLines = 1,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (chosen) colors.honey else colors.surface)
            .toggleable(value = chosen, role = Role.RadioButton, onValueChange = { if (it) onClick() })
            .semantics { stateDescription = if (chosen) "Chosen" else "Not chosen" }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/**
 * One of a few options stacked in a list (what you two are, where you live): a coloured badge, a name
 * and a line about it, and a mark on the right that fills in, with the word "Chosen", when it's picked.
 */
@Composable
fun ChoiceCard(
    title: String,
    detail: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Color = OllTheme.colors.leaf,
    icon: ImageVector? = null,
) {
    val colors = OllTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                if (chosen) drawRoundRect(colors.ink, cornerRadius = CornerRadius(14.dp.toPx()))
            }
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .toggleable(value = chosen, role = Role.RadioButton, onValueChange = { if (it) onClick() })
            .semantics { stateDescription = if (chosen) "Chosen" else "Not chosen" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.onFill, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OllType.button, color = colors.ink)
            Text(detail, style = OllType.caption, color = colors.inkMuted)
        }
        if (chosen) Text("Chosen", style = OllType.tag, color = colors.ink)
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(if (chosen) colors.ink else colors.line).padding(if (chosen) 6.dp else 2.dp),
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().clip(CircleShape).background(colors.surfaceRaised))
        }
    }
}
