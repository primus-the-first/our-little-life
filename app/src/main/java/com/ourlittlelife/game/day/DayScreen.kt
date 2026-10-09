package com.ourlittlelife.game.day

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ourlittlelife.game.ui.components.NeedBar
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.Clock
import com.ourlittlelife.rules.Need

@Composable
fun DayScreen(viewModel: DayViewModel = viewModel()) {
    val colors = OllTheme.colors
    val character = viewModel.character
    val clock = character.clock

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding(),
    ) {
        // The day and clock stay pinned while the needs and actions scroll underneath.
        Header(
            name = character.name,
            clock = clock,
            mood = character.needs.mood,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card {
                    Text("Needs", style = OllType.heading, color = colors.ink, modifier = Modifier.semantics { heading() })
                    Need.entries.forEach { need ->
                        NeedBar(label = need.label, value = character.needs[need])
                    }
                }
            }

            viewModel.message?.let { message ->
                item {
                    Card { Text(message, style = OllType.body, color = colors.ink) }
                }
            }

            if (character.asleep) {
                item {
                    Card {
                        Text("${character.name} is asleep.", style = OllType.heading, color = colors.ink)
                        Text(
                            "In the shared world, the next day starts once your partner goes to bed too.",
                            style = OllType.body,
                            color = colors.inkMuted,
                        )
                        PrimaryButton(
                            text = "Start ${Clock.startOfDay(clock.day + 1).weekday}",
                            onClick = viewModel::startNextDay,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item {
                    Text("What now?", style = OllType.heading, color = colors.ink, modifier = Modifier.semantics { heading() })
                }
                items(viewModel.actions, key = { it.id }) { action ->
                    val fits = action.minutes <= clock.minutesLeft
                    SecondaryButton(
                        text = action.name,
                        detail = describe(action),
                        onClick = { viewModel.perform(action) },
                        enabled = fits,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PrimaryButton(
                        text = "Go to bed",
                        detail = if (clock.minutesLeft > 0) "${formatDuration(clock.minutesLeft)} unused" else null,
                        onClick = viewModel::sleep,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, clock: Clock, mood: Int, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Day ${clock.day} · ${clock.weekday}",
                style = OllType.display,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "${formatTime(clock.minute)} · ${formatDuration(clock.minutesLeft)} left",
                style = OllType.stat,
                color = colors.inkMuted,
            )
        }
        val word = moodWord(mood)
        val chipColor = when (word) {
            "Cheerful" -> colors.leaf
            "Fine" -> colors.honey
            else -> colors.surfaceSunken
        }
        Text(
            "$name · $word",
            style = OllType.caption,
            color = if (word == "Grumpy") colors.berry else colors.onFill,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(chipColor)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OllTheme.colors.surfaceRaised)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}
