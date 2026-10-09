package com.ourlittlelife.game.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType

private val ButtonShape = RoundedCornerShape(12.dp)

/**
 * The one main action on a screen: peach fill, hard 3dp shadow, and it presses down flat.
 * Disabled buttons should say what's missing ("Need 20 bricks"), not just grey out.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val colors = OllTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val raised = enabled && !pressed

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .offset(y = if (pressed) 3.dp else 0.dp)
            .drawBehind {
                if (raised) {
                    drawRoundRect(
                        color = colors.popShadow,
                        topLeft = Offset(0f, 3.dp.toPx()),
                        size = size,
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
            }
            .clip(ButtonShape)
            .background(if (enabled) colors.peach else colors.surfaceSunken)
            .clickable(interaction, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = OllType.button, color = if (enabled) colors.onFill else colors.inkMuted)
        if (detail != null) {
            Text(detail, style = OllType.caption, color = if (enabled) colors.onFill else colors.inkMuted)
        }
    }
}

/** Other actions beside a primary: raised surface with a 1.5dp outline. Also used for small floating toggles. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val colors = OllTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .background(colors.surfaceRaised)
            .drawBehind {
                val inset = 0.75.dp.toPx()
                drawRoundRect(
                    color = colors.inkMuted,
                    topLeft = Offset(inset, inset),
                    size = size.copy(width = size.width - inset * 2, height = size.height - inset * 2),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = OllType.button, color = if (enabled) colors.ink else colors.inkMuted)
        if (detail != null) {
            Text(detail, style = OllType.caption, color = colors.inkMuted)
        }
    }
}
