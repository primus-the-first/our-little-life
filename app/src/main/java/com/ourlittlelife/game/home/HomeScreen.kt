package com.ourlittlelife.game.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ourlittlelife.game.day.describe
import com.ourlittlelife.game.day.describeShortfall
import com.ourlittlelife.game.day.formatDuration
import com.ourlittlelife.game.day.formatTime
import com.ourlittlelife.game.day.moodWord
import com.ourlittlelife.game.ui.components.NeedBar
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.Clock
import com.ourlittlelife.rules.Gathering
import com.ourlittlelife.rules.Need
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.PlotObject
import com.ourlittlelife.rules.Resource
import com.ourlittlelife.rules.Storage
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: GameViewModel = viewModel()) {
    val colors = OllTheme.colors
    val character = viewModel.character
    val clock = character.clock
    var needsOpen by remember { mutableStateOf(false) }

    // Notices sit over the bottom of the map, so they fade out on their own instead of blocking taps.
    LaunchedEffect(viewModel.message) {
        if (viewModel.message != null) {
            delay(3500)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding(),
    ) {
        Hud(
            clock = clock,
            name = character.name,
            mood = character.needs.mood,
            onOpenNeeds = { needsOpen = true },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        ResourceStrip(
            storage = viewModel.camp.storage,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            PlotCanvas(
                plot = viewModel.plot,
                characters = listOf(
                    PlacedCharacter(character.name, Sprites.jo, viewModel.characterTile.first, viewModel.characterTile.second, colors.peach),
                    PlacedCharacter(viewModel.partnerName, Sprites.maya, viewModel.partnerTile.first, viewModel.partnerTile.second, colors.sky),
                ),
                selected = viewModel.selected,
                focusTile = 11 to 11,
                onTapTile = viewModel::tapTile,
                modifier = Modifier.fillMaxSize(),
            )

            val lowest = Need.entries.minBy { character.needs[it] }
            val note = viewModel.message ?: if (character.needs[lowest] < 30) {
                "${lowest.label} is low. Tap something nearby that helps."
            } else {
                null
            }
            if (note != null) {
                Notice(
                    text = note,
                    low = viewModel.message == null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .clickable(onClick = viewModel::clearMessage),
                )
            }
        }

        Box(Modifier.padding(16.dp)) {
            if (character.asleep) {
                PrimaryButton(
                    text = "Start ${Clock.startOfDay(clock.day + 1).weekday}",
                    detail = "${character.name} is asleep",
                    onClick = viewModel::startNextDay,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PrimaryButton(
                    text = "Go to bed",
                    detail = if (clock.minutesLeft > 0) "${formatDuration(clock.minutesLeft)} unused" else null,
                    onClick = viewModel::sleep,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    viewModel.selected?.let { obj ->
        ObjectSheet(
            obj = obj,
            viewModel = viewModel,
            minutesLeft = clock.minutesLeft,
            asleep = character.asleep,
        )
    }

    if (needsOpen) {
        NeedsSheet(viewModel = viewModel, onDismiss = { needsOpen = false })
    }
}

@Composable
private fun Hud(clock: Clock, name: String, mood: Int, onOpenNeeds: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Day ${clock.day} · ${clock.weekday}",
                style = OllType.title,
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
            style = OllType.button,
            color = if (word == "Grumpy") colors.berry else colors.onFill,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(chipColor)
                .clickable(role = Role.Button, onClickLabel = "Show needs", onClick = onOpenNeeds)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }
}

/** What's in the shared chest, always in view. Refined materials appear once there are some. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResourceStrip(storage: Storage, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val shown = Resource.entries.filter { !it.refined || storage[it] > 0 }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { resource ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceSunken)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(resource.label, style = OllType.caption, color = colors.inkMuted)
                Text("${storage[resource]}", style = OllType.stat, color = colors.ink)
            }
        }
    }
}

@Composable
private fun Notice(text: String, low: Boolean, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (low) {
            Text("LOW", style = OllType.tag, color = colors.berry)
        }
        Text(text, style = OllType.body, color = colors.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectSheet(obj: PlotObject, viewModel: GameViewModel, minutesLeft: Int, asleep: Boolean) {
    val colors = OllTheme.colors
    val actions = viewModel.actionsFor(obj)
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissSelection,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val usesLeft = viewModel.camp.usesLeft(obj)
            val perDay = Gathering.usesPerDay(obj.kind)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(obj.kind.label, style = OllType.title, color = colors.ink, modifier = Modifier.semantics { heading() })
                if (perDay != Int.MAX_VALUE) {
                    Text("$usesLeft of $perDay left today", style = OllType.caption, color = colors.inkMuted)
                }
            }
            when {
                asleep -> Text("Everyone's asleep. Start the next day first.", style = OllType.body, color = colors.inkMuted)
                obj.kind == ObjectKind.CHEST -> ChestContents(viewModel.camp.storage)
                actions.isEmpty() -> Text(comingSoon(obj.kind), style = OllType.body, color = colors.inkMuted)
                else -> actions.forEach { action ->
                    val fits = action.minutes <= minutesLeft
                    val grown = usesLeft > 0
                    val short = viewModel.camp.storage.shortfall(action.costs)
                    SecondaryButton(
                        text = action.name,
                        detail = when {
                            !grown -> "Grows back tomorrow"
                            short.isNotEmpty() -> describeShortfall(short)
                            !fits -> "Not enough time today"
                            else -> describe(action)
                        },
                        enabled = fits && grown && short.isEmpty(),
                        onClick = { viewModel.perform(obj, action) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun comingSoon(kind: ObjectKind): String = "Nothing to do here yet."

@Composable
private fun ChestContents(storage: Storage) {
    val colors = OllTheme.colors
    val empty = Resource.entries.all { storage[it] == 0 }
    if (empty) {
        Text(
            "Empty for now. Chop trees, break rocks and pick bushes, and everything you gather lands here for both of you.",
            style = OllType.body,
            color = colors.inkMuted,
        )
        return
    }
    Resource.entries.forEach { resource ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(resource.label, style = OllType.body, color = colors.ink)
            Text("${storage[resource]}", style = OllType.stat, color = colors.ink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeedsSheet(viewModel: GameViewModel, onDismiss: () -> Unit) {
    val colors = OllTheme.colors
    val character = viewModel.character
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${character.name}'s needs", style = OllType.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            Need.entries.forEach { need -> NeedBar(label = need.label, value = character.needs[need]) }
        }
    }
}
