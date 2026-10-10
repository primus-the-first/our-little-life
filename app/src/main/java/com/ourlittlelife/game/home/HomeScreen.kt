package com.ourlittlelife.game.home

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import com.ourlittlelife.game.BuildConfig
import com.ourlittlelife.rules.Building
import com.ourlittlelife.rules.Clock
import com.ourlittlelife.rules.Gathering
import com.ourlittlelife.rules.Placement
import com.ourlittlelife.rules.Room
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.Need
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.PlotObject
import com.ourlittlelife.rules.Resource
import com.ourlittlelife.rules.Storage
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: GameViewModel = viewModel(), onBackToLobby: () -> Unit = {}) {
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

    // The map opens centred on wherever Jo is standing (also after the phone is turned).
    val focus = remember { viewModel.characterTile }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (landscape) {
        // Landscape: the day, materials and bed button in a side panel; the map gets the rest.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding(),
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Hud(clock, character.name, character.needs.mood, onOpenNeeds = { needsOpen = true }, stacked = true)
                PartnerLine(viewModel)
                ResourceStrip(storage = viewModel.camp.storage)
                Spacer(Modifier.weight(1f))
                DayButton(viewModel)
            }
            MapArea(
                viewModel = viewModel,
                focus = focus,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    } else {
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
            PartnerLine(viewModel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            ResourceStrip(
                storage = viewModel.camp.storage,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
            MapArea(
                viewModel = viewModel,
                focus = focus,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            Box(Modifier.padding(16.dp)) { DayButton(viewModel) }
        }
    }

    when (val selection = viewModel.selection) {
        is Selection.Thing -> ObjectSheet(selection.obj, viewModel, clock.minutesLeft, character.asleep)
        is Selection.InRoom -> RoomSheet(selection.room, viewModel, clock.minutesLeft, character.asleep)
        is Selection.Ground -> GroundSheet(selection, viewModel, clock.minutesLeft, character.asleep)
        null -> Unit
    }

    if (needsOpen) {
        NeedsSheet(viewModel = viewModel, onDismiss = { needsOpen = false }, onBackToLobby = onBackToLobby)
    }
}

/** The plot with its notices and the build toggle laid over it. */
@Composable
private fun MapArea(viewModel: GameViewModel, focus: Pair<Int, Int>, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val character = viewModel.character
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        val selection = viewModel.selection
        val selectedArea = when (selection) {
            is Selection.Thing -> selection.obj.let { TileArea(it.x, it.y, it.kind.width, it.kind.height) }
            is Selection.InRoom -> selection.room.let { TileArea(it.x, it.y, it.kind.width, it.kind.height) }
            is Selection.Ground, null -> null
        }
        // On empty ground, preview the first room on offer right where you tapped.
        val ghost = (selection as? Selection.Ground)?.let { ground ->
            viewModel.buildOptions().firstOrNull()?.let { kind ->
                Ghost(
                    area = TileArea(ground.x, ground.y, kind.width, kind.height),
                    fits = viewModel.placement(kind, ground.x, ground.y) == Placement.Ok,
                )
            }
        }
        PlotCanvas(
            plot = viewModel.plot,
            characters = listOfNotNull(
                PlacedCharacter(character.name, viewModel.myOutfit, viewModel.characterTile.first, viewModel.characterTile.second, colors.peach),
                viewModel.partner?.let { other ->
                    viewModel.partnerTile?.let { (x, y) ->
                        PlacedCharacter(other.character.name, viewModel.outfitFor(other.id), x, y, colors.sky)
                    }
                },
            ),
            selected = selectedArea,
            ghost = ghost,
            focusTile = focus,
            onTapTile = viewModel::tapTile,
            modifier = Modifier.fillMaxSize(),
        )

        val lowest = Need.entries.minBy { character.needs[it] }
        val lowNeed = character.needs[lowest] < 30
        val note = when {
            viewModel.message != null -> viewModel.message
            viewModel.buildMode -> "Build mode: tap open ground to place a room."
            lowNeed -> "${lowest.label} is low. Tap something nearby that helps."
            else -> null
        }
        if (note != null) {
            Notice(
                text = note,
                low = viewModel.message == null && !viewModel.buildMode && lowNeed,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .clickable(onClick = viewModel::clearMessage),
            )
        }

        // A tap on grass normally walks there; build mode turns it into "place a room here".
        SecondaryButton(
            text = if (viewModel.buildMode) "Done building" else "Build",
            onClick = viewModel::toggleBuildMode,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        )
    }
}

/** The screen's one primary action: go to bed. Once you're asleep it shows who the day is waiting for. */
@Composable
private fun DayButton(viewModel: GameViewModel) {
    val character = viewModel.character
    val clock = character.clock
    if (character.asleep) {
        PrimaryButton(
            text = "Waiting for ${viewModel.partnerName ?: "your friend"}",
            detail = "${Clock.startOfDay(clock.day + 1).weekday} starts when they sleep",
            onClick = {},
            enabled = false,
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

/** The day, the clock and the mood chip. [stacked] puts the chip under the clock, for the narrow landscape panel. */
@Composable
private fun Hud(
    clock: Clock,
    name: String,
    mood: Int,
    onOpenNeeds: () -> Unit,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    if (stacked) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HudContent(clock, name, mood, onOpenNeeds)
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            HudContent(clock, name, mood, onOpenNeeds)
        }
    }
}

@Composable
private fun HudContent(clock: Clock, name: String, mood: Int, onOpenNeeds: () -> Unit) {
    val colors = OllTheme.colors
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

/** Your partner at a glance, or, before anyone has joined, the code to send them. */
@Composable
private fun PartnerLine(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val partner = viewModel.partner
    val text = when {
        partner == null -> "Waiting for a friend · code ${viewModel.worldCode}"
        partner.character.asleep -> "${partner.character.name} is asleep"
        else -> "${partner.character.name} is up · ${formatTime(partner.character.clock.minute)}"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (partner == null) colors.surfaceSunken else colors.sky)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = OllType.caption, color = if (partner == null) colors.ink else colors.onFill)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSheet(room: Room, viewModel: GameViewModel, minutesLeft: Int, asleep: Boolean) {
    val colors = OllTheme.colors
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissSelection,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(room.kind.label, style = OllType.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            if (room.kind == RoomKind.CABIN) {
                Text("A real roof. Nights in here restore all your energy.", style = OllType.body, color = colors.inkMuted)
            }
            if (asleep) {
                Text("Everyone's asleep. Start the next day first.", style = OllType.body, color = colors.inkMuted)
            } else {
                viewModel.actionsFor(room).forEach { action ->
                    val fits = action.minutes <= minutesLeft
                    SecondaryButton(
                        text = action.name,
                        detail = if (fits) describe(action) else "Not enough time today",
                        enabled = fits,
                        onClick = { viewModel.perform(room, action) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Tapping empty grass: offer to build a room there, with the outline already shown on the map. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroundSheet(ground: Selection.Ground, viewModel: GameViewModel, minutesLeft: Int, asleep: Boolean) {
    val colors = OllTheme.colors
    val options = viewModel.buildOptions()
    ModalBottomSheet(
        onDismissRequest = viewModel::dismissSelection,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
        scrimColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Build here?", style = OllType.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            when {
                asleep -> Text("Everyone's asleep. Start the next day first.", style = OllType.body, color = colors.inkMuted)
                options.isEmpty() -> Text("Every room is built. Time to decorate.", style = OllType.body, color = colors.inkMuted)
                else -> {
                    val first = options.first()
                    Text(
                        "The outline shows where the ${first.label.lowercase()} would go (${first.width} × ${first.height} tiles). Its top-left corner sits where you tapped.",
                        style = OllType.body,
                        color = colors.inkMuted,
                    )
                    options.forEach { kind ->
                        val placement = viewModel.placement(kind, ground.x, ground.y)
                        val short = viewModel.camp.storage.shortfall(kind.costs)
                        val time = Building.actionFor(kind).minutes
                        val ready = placement == Placement.Ok && short.isEmpty() && time <= minutesLeft
                        SecondaryButton(
                            text = "Build the ${kind.label.lowercase()} here",
                            detail = when {
                                placement != Placement.Ok -> placementProblem(placement)
                                short.isNotEmpty() -> describeShortfall(short)
                                time > minutesLeft -> "Not enough time today"
                                else -> describe(Building.actionFor(kind))
                            },
                            enabled = ready,
                            onClick = { viewModel.build(kind, ground.x, ground.y) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun placementProblem(placement: Placement): String = when (placement) {
    Placement.OffPlot -> "Too close to the edge"
    Placement.Blocked -> "Something's in the way"
    Placement.CabinFirst -> "Build the cabin first"
    Placement.NotConnected -> "Has to join an existing room"
    Placement.AlreadyBuilt -> "Already built"
    Placement.Ok -> ""
}

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
private fun NeedsSheet(viewModel: GameViewModel, onDismiss: () -> Unit, onBackToLobby: () -> Unit) {
    val colors = OllTheme.colors
    val character = viewModel.character
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${character.name}'s needs", style = OllType.title, color = colors.ink, modifier = Modifier.semantics { heading() })
            Need.entries.forEach { need -> NeedBar(label = need.label, value = character.needs[need]) }
            if (BuildConfig.DEBUG) {
                SecondaryButton(
                    text = "Debug: fill the chest",
                    detail = "Test builds only",
                    onClick = { viewModel.debugFillChest(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text("This world", style = OllType.heading, color = colors.ink, modifier = Modifier.semantics { heading() })
            Text(
                "Code ${viewModel.worldCode}. Share it so a friend can join" +
                    (viewModel.partnerName?.let { ". You're playing with $it." } ?: "."),
                style = OllType.body,
                color = colors.inkMuted,
            )
            SecondaryButton(
                text = "Back to lobby",
                detail = "Switch worlds or join a friend",
                onClick = { onDismiss(); onBackToLobby() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
