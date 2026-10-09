package com.ourlittlelife.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType

private const val LOW = 30
private const val GOOD = 60

/** One need from 0 to 100. Below 30 it also says "Low" in words, so state never relies on colour. */
@Composable
fun NeedBar(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val fill = when {
        value >= GOOD -> colors.leaf
        value >= LOW -> colors.honey
        else -> colors.berry
    }
    val fraction by animateFloatAsState(value / 100f, tween(200), label = "need")

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label $value of 100" + if (value < LOW) ", low" else ""
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(label, style = OllType.caption, color = colors.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (value < LOW) {
                    Text("LOW", style = OllType.tag, color = colors.berry)
                }
                Text("$value", style = OllType.stat, color = colors.ink)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceSunken),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(50))
                    .background(fill),
            )
        }
    }
}
