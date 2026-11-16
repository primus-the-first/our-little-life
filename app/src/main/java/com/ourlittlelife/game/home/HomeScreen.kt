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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ourlittlelife.game.day.describe
import com.ourlittlelife.game.day.describeShortfall
import com.ourlittlelife.game.day.formatDuration
import com.ourlittlelife.game.day.formatTime
import com.ourlittlelife.game.day.moodWord
import com.ourlittlelife.game.day.factsOf
import com.ourlittlelife.game.ui.components.ActionRow
import com.ourlittlelife.game.ui.components.BarItem
import com.ourlittlelife.game.ui.components.ChoiceChip
import com.ourlittlelife.game.ui.components.Fact
import com.ourlittlelife.game.ui.components.NeedBar
import com.ourlittlelife.game.ui.components.NeedMeter
import com.ourlittlelife.game.ui.components.OllIcons
import com.ourlittlelife.game.ui.components.PopCard
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.components.SettingSwitch
import com.ourlittlelife.game.ui.components.StatChip
import com.ourlittlelife.game.ui.components.StatusChip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import com.ourlittlelife.rules.Needs
import com.ourlittlelife.rules.Together
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ourlittlelife.game.BuildConfig
import com.ourlittlelife.rules.Building
import com.ourlittlelife.rules.Clock
import com.ourlittlelife.rules.Gathering
import com.ourlittlelife.rules.FurnitureCategory
import com.ourlittlelife.rules.FurnitureKind
import com.ourlittlelife.rules.FurniturePlacement
import com.ourlittlelife.rules.Crop
import com.ourlittlelife.rules.Gardening
import com.ourlittlelife.rules.Gesture
import com.ourlittlelife.rules.Interiors
import com.ourlittlelife.rules.Placement
import com.ourlittlelife.rules.Walks
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
    val clock = viewModel.shownClock

    // The clock runs while the game is on screen: one game minute every real second, like The Sims.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(1000)
                viewModel.tick()
            }
        }
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var inviteOpen by remember { mutableStateOf(false) }
    var bedOpen by remember { mutableStateOf(false) }
    var privateOpen by remember { mutableStateOf(false) }
    val onTogether = { if (viewModel.partner != null) viewModel.showPartner() else inviteOpen = true }

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
        // Landscape: the day, needs, materials and the bar in a side panel; the map gets the rest.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding(),
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopLine(clock, coins = viewModel.camp.storage[Resource.COINS], onSettings = { settingsOpen = true })
                NeedsCard(character.needs, onClick = viewModel::showMe)
                StatusStrip(viewModel, wrap = true)
                Spacer(Modifier.weight(1f))
                BottomBar(viewModel, onMe = viewModel::showMe, onTogether = onTogether, onBed = { bedOpen = true })
            }
            MapArea(
                viewModel = viewModel,
                focus = focus,
                onPrivate = { privateOpen = true },
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
            TopLine(
                clock = clock,
                coins = viewModel.camp.storage[Resource.COINS],
                onSettings = { settingsOpen = true },
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
            )
            NeedsCard(character.needs, onClick = viewModel::showMe, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            StatusStrip(viewModel, modifier = Modifier.padding(bottom = 10.dp))
            MapArea(
                viewModel = viewModel,
                focus = focus,
                onPrivate = { privateOpen = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            BottomBar(
                viewModel,
                onMe = viewModel::showMe,
                onTogether = onTogether,
                onBed = { bedOpen = true },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .drawBehind { drawRect(colors.popShadow.copy(alpha = colors.popShadow.alpha / 2), topLeft = Offset(0f, -2.dp.toPx()), size = size.copy(height = 2.dp.toPx())) }
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    when (val selection = viewModel.selection) {
        is Selection.Thing -> ObjectSheet(selection.obj, viewModel, character.asleep)
        is Selection.InRoom -> RoomSheet(selection.room, viewModel, character.asleep)
        is Selection.Ground -> GroundSheet(selection, viewModel, character.asleep)
        is Selection.Person -> PersonSheet(selection.playerId, viewModel, onPrivate = { privateOpen = true })
        is Selection.Piece -> PieceSheet(selection, viewModel, character.asleep)
        null -> Unit
    }

    if (viewModel.stargazing) {
        val bonus = viewModel.stargazeSocialPreview()
        Dialog(
            onDismissRequest = viewModel::finishStargazing,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            StarSky(
                people = listOfNotNull(viewModel.myOutfit, viewModel.partner?.takeIf { bonus > 0 }?.let { viewModel.outfitFor(it.id) }),
                companionName = viewModel.partnerName,
                socialBonus = bonus,
                onClose = viewModel::finishStargazing,
                scene3d = if (viewModel.view3d) { modifier ->
                    NightHill3D(
                        looks = listOfNotNull(viewModel.lookFor(viewModel.myPlayerId), viewModel.partner?.takeIf { bonus > 0 }?.let { viewModel.lookFor(it.id) }),
                        modifier = modifier,
                    )
                } else null,
            )
        }
    }

    if (viewModel.fishing) {
        Dialog(
            onDismissRequest = viewModel::closeFishing,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            FishingPond(
                log = viewModel.myFishLog,
                onLand = viewModel::landFish,
                onMiss = viewModel::missFish,
                onClose = viewModel::closeFishing,
            )
        }
    }

    if (viewModel.racing) {
        val other = viewModel.partner
        Dialog(
            onDismissRequest = viewModel::closeRace,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            RaceTrack(
                runner = viewModel.myOutfit,
                ghost = other?.takeIf { it.bestLapMillis != null }?.let { viewModel.outfitFor(it.id) },
                ghostName = other?.character?.name,
                ghostMillis = other?.bestLapMillis,
                bestMillis = viewModel.myBestLap,
                stamina = viewModel.startStamina,
                outcome = viewModel.lastRace,
                onFinish = viewModel::finishLap,
                onAgain = viewModel::raceAgain,
                onClose = viewModel::closeRace,
                track3d = if (viewModel.view3d) { runner, ghost, running, modifier ->
                    RaceTrack3D(
                        runnerLook = viewModel.lookFor(viewModel.myPlayerId),
                        ghostLook = other?.takeIf { it.bestLapMillis != null }?.let { viewModel.lookFor(it.id) },
                        runner = runner,
                        ghost = ghost,
                        running = running,
                        modifier = modifier,
                    )
                } else null,
            )
        }
    }

    if (settingsOpen) SettingsSheet(viewModel = viewModel, onDismiss = { settingsOpen = false }, onBackToLobby = onBackToLobby)
    if (inviteOpen) InviteSheet(viewModel = viewModel, onDismiss = { inviteOpen = false })
    if (bedOpen) BedSheet(viewModel = viewModel, onDismiss = { bedOpen = false })
    if (privateOpen) PrivateSpace(viewModel = viewModel, onClose = { privateOpen = false })
}

/** The plot with its notices and the build toggle laid over it. */
@Composable
private fun MapArea(viewModel: GameViewModel, focus: Pair<Int, Int>, modifier: Modifier = Modifier, onPrivate: () -> Unit = {}) {
    val colors = OllTheme.colors
    val character = viewModel.character
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp))) {
        val selection = viewModel.selection
        val selectedArea = when (selection) {
            is Selection.Thing -> selection.obj.let { TileArea(it.x, it.y, it.kind.width, it.kind.height) }
            is Selection.InRoom -> selection.room.let { TileArea(it.x, it.y, it.kind.width, it.kind.height) }
            is Selection.Person, is Selection.Ground, is Selection.Piece, null -> null
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
        val room = viewModel.insideRoom
        if (room != null) {
            val held = viewModel.holding
            val roomGhost = held?.let { Ghost(TileArea(it.x, it.y, it.kind.width, it.kind.height), viewModel.holdingPlacement() == FurniturePlacement.Ok) }
            if (viewModel.view3d) Room3D(
                room = room,
                figures = listOfNotNull(
                    Figure3D(
                        character.name, viewModel.insideTile.first, viewModel.insideTile.second, viewModel.lookFor(viewModel.myPlayerId),
                        pose = viewModel.myPose,
                        restAt = viewModel.myRest(room),
                        faceTo = viewModel.myFaceTo,
                    ),
                    viewModel.partnerInHere?.let { other ->
                        other.inside?.let {
                            val shown = viewModel.shownAs(other, room)
                            Figure3D(other.character.name, it.x, it.y, viewModel.lookFor(other.id), pose = shown.pose, restAt = shown.rest, faceTo = shown.faceTo)
                        }
                    },
                ),
                focus = Interiors.width(room.kind) / 2 to Interiors.height(room.kind) / 2,
                selected = (selection as? Selection.Piece)?.piece,
                ghost = roomGhost,
                onTapTile = viewModel::tapInside,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (viewModel.furnishing) TRAY_HEIGHT else 0.dp),
            ) else RoomCanvas(
                room = room,
                characters = listOfNotNull(
                    PlacedCharacter(character.name, viewModel.myOutfit, viewModel.insideTile.first, viewModel.insideTile.second, colors.peach),
                    viewModel.partnerInHere?.let { other ->
                        other.inside?.let { PlacedCharacter(other.character.name, viewModel.outfitFor(other.id), it.x, it.y, colors.sky) }
                    },
                ),
                selected = (selection as? Selection.Piece)?.piece,
                ghost = roomGhost,
                onTapTile = viewModel::tapInside,
                // With the tray open, the room sits above it so every tile can still be tapped.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (viewModel.furnishing) TRAY_HEIGHT else 0.dp),
            )
        } else if (viewModel.view3d) {
            World3D(
                plot = viewModel.plot,
                figures = listOfNotNull(
                    Figure3D(
                        character.name, viewModel.characterTile.first, viewModel.characterTile.second, viewModel.lookFor(viewModel.myPlayerId),
                        pose = viewModel.myPose,
                        restAt = viewModel.myRest(null),
                        faceTo = viewModel.myFaceTo,
                    ),
                    viewModel.partner?.takeIf { it.inside == null }?.let { other ->
                        viewModel.partnerTile?.let { (x, y) ->
                            val shown = viewModel.shownAs(other, null)
                            Figure3D(other.character.name, x, y, viewModel.lookFor(other.id), pose = shown.pose, restAt = shown.rest, faceTo = shown.faceTo)
                        }
                    },
                ),
                focus = viewModel.characterTile,
                onTapTile = viewModel::tapTile,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PlotCanvas(
                plot = viewModel.plot,
                characters = listOfNotNull(
                    PlacedCharacter(character.name, viewModel.myOutfit, viewModel.characterTile.first, viewModel.characterTile.second, colors.peach),
                    // Someone indoors isn't drawn on the map; their line at the top says where they are.
                    viewModel.partner?.takeIf { it.inside == null }?.let { other ->
                        viewModel.partnerTile?.let { (x, y) ->
                            PlacedCharacter(other.character.name, viewModel.outfitFor(other.id), x, y, colors.sky)
                        }
                    },
                ),
                selected = selectedArea,
                ghost = ghost,
                focusTile = if (viewModel.touring) viewModel.characterTile else focus,
                follow = viewModel.touring,
                showYard = viewModel.buildMode,
                onTapTile = viewModel::tapTile,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val note = when {
            viewModel.needsUpdate -> "This world was saved by a newer version of the game. Install the latest APK to keep playing."
            viewModel.message != null -> viewModel.message
            room != null -> null
            viewModel.buildMode -> "Build mode: tap open ground to place a room."
            character.asleep -> viewModel.partnerName?.let { "Asleep. ${Clock.startOfDay(character.clock.day + 1).weekday} starts when $it sleeps too." }
            else -> null
        }
        val inviter = viewModel.walkInviteFrom
        val doing = viewModel.busy
        // Cards sit at the bottom of the map, in reach of a thumb, and above the furniture tray when it's open.
        val cards = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 12.dp, end = 12.dp, bottom = if (viewModel.furnishing) TRAY_HEIGHT + 12.dp else 12.dp)
        if (doing != null) {
            BusyCard(doing, onHurry = viewModel::hurry, onStop = viewModel::stopBusy, modifier = cards)
        } else if (inviter != null && !viewModel.touring) {
            WalkCard(
                text = "${inviter.character.name} wants to go for a walk in the forest.",
                yes = "Go for a walk" to viewModel::acceptWalk,
                no = "Not now" to viewModel::declineWalk,
                modifier = cards,
            )
        } else if (viewModel.askedForWalk && viewModel.message == null) {
            WalkCard(
                text = "Waiting for ${viewModel.partnerName ?: "your friend"} to say yes to a walk.",
                yes = null,
                no = "Call it off" to viewModel::declineWalk,
                modifier = cards,
            )
        } else if (viewModel.momentInvite != null && viewModel.message == null) {
            // Says who, never what: the rest is behind the PIN.
            WalkCard(
                text = "${viewModel.momentInvite?.first?.character?.name} is thinking of you.",
                yes = "Open" to onPrivate,
                no = "Not now" to viewModel::declineMoment,
                modifier = cards,
            )
        } else if (note != null) {
            Notice(text = note, onClick = viewModel::clearMessage, modifier = cards)
        }

        if (room != null) {
            Text(
                room.kind.label,
                style = OllType.heading,
                color = colors.ink,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .semantics { heading() },
            )
            if (viewModel.furnishing) {
                FurnishTray(viewModel, modifier = Modifier.align(Alignment.BottomCenter))
            } else {
                PopCard(onClick = viewModel::goOutside, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(OllIcons.outside, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                        Text("Head outside", style = OllType.button, color = colors.ink)
                    }
                }
            }
        }
    }
}

/** The day and the clock, your coins, and the way into settings. */
@Composable
private fun TopLine(clock: Clock, coins: Int, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Day ${clock.day} · ${clock.weekday}", style = OllType.title, color = colors.ink, maxLines = 1, modifier = Modifier.semantics { heading() })
            Text(
                if (clock.late) "${formatTime(clock.minute)} · Late" else formatTime(clock.minute),
                style = OllType.stat,
                color = if (clock.late) colors.peachText else colors.inkMuted,
            )
        }
        PopCard(color = colors.honey, radius = 8.dp) {
            StatChip(value = "%,d".format(coins), label = "Coins", icon = OllIcons.coins, fill = colors.honey)
        }
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Settings", onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(OllIcons.settings, contentDescription = "Settings", tint = colors.ink, modifier = Modifier.size(24.dp))
        }
    }
}

/** Your five needs, always in view. Tap for the full picture. */
@Composable
private fun NeedsCard(needs: Needs, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PopCard(modifier = modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "Show your needs") {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Need.entries.forEach { need ->
                NeedMeter(label = need.label, value = needs[need], icon = OllIcons.of(need), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Who's around, then what's in your chest, on one line you can swipe along. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusStrip(viewModel: GameViewModel, modifier: Modifier = Modifier, wrap: Boolean = false) {
    val colors = OllTheme.colors
    val partner = viewModel.partner
    val storage = viewModel.camp.storage
    val presence = when {
        viewModel.isSolo -> "Playing solo"
        partner == null -> "Waiting for a friend · code ${viewModel.worldCode}"
        partner.character.asleep -> "${partner.character.name} is asleep"
        partner.inside != null -> "${partner.character.name} is in the ${viewModel.plot.rooms.firstOrNull { it.id == partner.inside?.roomId }?.kind?.label?.lowercase() ?: "house"}"
        else -> "${partner.character.name} is up · ${formatTime(partner.character.clock.minute)}"
    }
    val shown = Resource.entries.filter { it != Resource.COINS && ((!it.refined && !it.food) || storage[it] > 0) }
    val chips: @Composable () -> Unit = {
        StatusChip(presence, fill = if (partner == null) colors.surfaceSunken else colors.sky, live = partner != null && !partner.character.asleep)
        shown.forEach { resource -> StatChip(value = "${storage[resource]}", label = resource.label, icon = OllIcons.of(resource)) }
    }
    if (wrap) {
        FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { chips() }
    }
}

/**
 * The bar along the bottom: you, your friend, building (or furnishing, indoors), and bed. Bed turns
 * peach, the screen's one main action, when it's late or you're worn out.
 */
@Composable
private fun BottomBar(viewModel: GameViewModel, onMe: () -> Unit, onTogether: () -> Unit, onBed: () -> Unit, modifier: Modifier = Modifier) {
    val character = viewModel.character
    val inside = viewModel.insideRoom != null
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BarItem("Me", OllIcons.me, onClick = onMe)
        BarItem("Together", OllIcons.together, onClick = onTogether)
        if (inside) {
            BarItem(if (viewModel.furnishing) "Done" else "Furnish", OllIcons.furnish, onClick = viewModel::toggleFurnishing, selected = viewModel.furnishing)
        } else {
            BarItem(if (viewModel.buildMode) "Done" else "Build", OllIcons.build, onClick = viewModel::toggleBuildMode, selected = viewModel.buildMode)
        }
        BarItem(
            label = if (character.asleep) "Asleep" else "Bed",
            icon = OllIcons.bed,
            onClick = onBed,
            enabled = !character.asleep,
            emphasis = character.clock.late || character.needs[Need.ENERGY] < 30,
        )
    }
}

@Composable
private fun Notice(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    PopCard(modifier = modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "Dismiss") {
        Text(text, style = OllType.body, color = colors.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

/** What you're in the middle of, how far along, and a way to hurry it or stop. */
@Composable
private fun BusyCard(busy: Busy, onHurry: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    PopCard(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(busy.label, style = OllType.heading, color = colors.ink)
            Text("${busy.elapsed.toInt()} of ${busy.minutes} min", style = OllType.caption, color = colors.inkMuted)
        }
        val done = (busy.elapsed / busy.minutes).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.surfaceSunken)
                .semantics { contentDescription = "${(done * 100).toInt()} percent done" },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(done)
                    .fillMaxHeight()
                    .background(colors.leaf),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(text = if (busy.hurry) "Hurrying" else "Hurry", enabled = !busy.hurry, onClick = onHurry, modifier = Modifier.weight(1f))
            SecondaryButton(text = "Stop", onClick = onStop, modifier = Modifier.weight(1f))
        }
    }
    }
}

/** A walk invite on the map: what's being asked, and the answers. */
@Composable
private fun WalkCard(text: String, yes: Pair<String, () -> Unit>?, no: Pair<String, () -> Unit>, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    PopCard(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = OllType.body, color = colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            yes?.let { (label, onClick) -> SecondaryButton(text = label, onClick = onClick, modifier = Modifier.weight(1f)) }
            SecondaryButton(text = no.first, onClick = no.second, modifier = if (yes != null) Modifier.weight(1f) else Modifier)
        }
    }
    }
}

/** Every sheet: a title (with an optional note beside it), then its contents, scrolling if they're long. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    title: String,
    onDismiss: () -> Unit,
    note: String? = null,
    scrim: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OllTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
        scrimColor = if (scrim) colors.onFill.copy(alpha = 0.45f) else Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(title, style = OllType.title, color = colors.ink, modifier = Modifier.semantics { heading() })
                if (note != null) Text(note, style = OllType.caption, color = colors.inkMuted)
            }
            content()
        }
    }
}

/** A heading inside a sheet. */
@Composable
private fun Section(text: String) {
    Text(text, style = OllType.heading, color = OllTheme.colors.ink, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** Sky for the things that are better together, honey for what earns coins, leaf for everything else. */
@Composable
private fun tintFor(action: com.ourlittlelife.rules.Action): Color {
    val colors = OllTheme.colors
    return when {
        action in Together.companyBonus || action.id.startsWith("stargaze") || action.id == "sit_sofa" -> colors.sky
        Resource.COINS in action.yields -> colors.honey
        else -> colors.leaf
    }
}

@Composable
private fun ObjectSheet(obj: PlotObject, viewModel: GameViewModel, asleep: Boolean) {
    val colors = OllTheme.colors
    val actions = viewModel.actionsFor(obj)
    val usesLeft = viewModel.camp.usesLeft(obj)
    val perDay = Gathering.usesPerDay(obj.kind)
    Sheet(
        title = obj.kind.label,
        note = if (perDay != Int.MAX_VALUE) "$usesLeft of $perDay left today" else null,
        onDismiss = viewModel::dismissSelection,
    ) {
        when {
            asleep -> Text("Everyone's asleep. Start the next day first.", style = OllType.body, color = colors.inkMuted)
            obj.kind == ObjectKind.CHEST -> ChestContents(viewModel.chestAt(obj))
            actions.isEmpty() -> Text(comingSoon(obj.kind), style = OllType.body, color = colors.inkMuted)
            else -> {
                val grown = usesLeft > 0
                // The first thing you can actually do is the sheet's main action.
                val main = actions.firstOrNull { grown && viewModel.camp.storage.shortfall(it.costs).isEmpty() }
                actions.forEach { action ->
                    val short = viewModel.camp.storage.shortfall(action.costs)
                    ActionRow(
                        title = action.name,
                        icon = OllIcons.of(action),
                        facts = factsOf(action),
                        problem = when {
                            !grown -> "Grows back tomorrow"
                            short.isNotEmpty() -> describeShortfall(short)
                            else -> null
                        },
                        enabled = grown && short.isEmpty(),
                        primary = action == main,
                        tint = tintFor(action),
                        onClick = { viewModel.perform(obj, action) },
                    )
                }
            }
        }
        if (!asleep && obj.kind == ObjectKind.TENT) SleepHereButton(viewModel)
    }
}

private val TRAY_HEIGHT = 196.dp

/**
 * The furniture tray: sorted by kind, each piece showing what it costs. Pick one and it appears in
 * the room; tap the floor to move it, then place it. Pieces you can't afford say what's missing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FurnishTray(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    var category by remember { mutableStateOf(FurnitureCategory.SEATING) }
    val held = viewModel.holding
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(TRAY_HEIGHT)
            .background(colors.surfaceRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (held != null) {
            val placement = viewModel.holdingPlacement()
            Text(
                if (held.moving != null) "Moving the ${held.kind.label.lowercase()}. Tap the floor where it should go."
                else "${held.kind.label} · ${formatCosts(held.kind.costs)}. Tap the floor where it should go.",
                style = OllType.body,
                color = colors.ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Place here",
                    detail = furnitureProblem(placement),
                    enabled = placement == FurniturePlacement.Ok,
                    onClick = viewModel::placeHeld,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(text = "Cancel", onClick = viewModel::cancelHolding)
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FurnitureCategory.entries.forEach { option ->
                    val chosen = option == category
                    Text(
                        option.label,
                        style = OllType.button,
                        color = if (chosen) colors.onFill else colors.ink,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (chosen) colors.honey else colors.surfaceSunken)
                            .selectable(selected = chosen, role = Role.Tab, onClick = { category = option })
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FurnitureKind.entries.filter { it.category == category }.forEach { kind ->
                    val short = viewModel.camp.storage.shortfall(kind.costs)
                    SecondaryButton(
                        text = kind.label,
                        detail = if (short.isEmpty()) formatCosts(kind.costs) else describeShortfall(short),
                        enabled = short.isEmpty(),
                        onClick = { viewModel.pick(kind) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton(text = "Done furnishing", onClick = viewModel::toggleFurnishing, modifier = Modifier.fillMaxWidth())
    }
}

/** "120 coins", or "30 planks, 20 cloth". */
private fun formatCosts(costs: Map<Resource, Int>): String =
    costs.entries.joinToString(", ") { (resource, amount) -> "$amount ${resource.label.lowercase()}" }

private fun furnitureProblem(placement: FurniturePlacement): String? = when (placement) {
    FurniturePlacement.Ok -> null
    FurniturePlacement.OffFloor -> "Doesn't fit there"
    FurniturePlacement.Blocked -> "Something's in the way"
    FurniturePlacement.BlocksDoor -> "Keep the door clear"
}

/** The garden's beds: water them all at once, plant empty ones, pick ripe ones. */
@Composable
private fun GardenBeds(garden: Room, viewModel: GameViewModel) {
    val colors = OllTheme.colors
    val thirsty = garden.beds.count { it.crop != null && !Gardening.ready(it) && !it.watered }
    ActionRow(
        title = "Water the beds",
        icon = OllIcons.of(Need.HYGIENE),
        facts = listOf(Fact(if (thirsty == 1) "1 bed needs water" else "$thirsty beds need water")) + factsOf(Gardening.watering),
        problem = if (thirsty > 0) null else "Nothing needs water",
        enabled = thirsty > 0,
        primary = thirsty > 0,
        onClick = { viewModel.water(garden) },
    )
    garden.beds.forEachIndexed { i, bed ->
        val crop = bed.crop
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when {
                    crop == null -> "Bed ${i + 1} · empty"
                    Gardening.ready(bed) -> "Bed ${i + 1} · ${crop.label} · ready"
                    else -> "Bed ${i + 1} · ${crop.label} · day ${bed.grown} of ${crop.days}" + if (bed.watered) " · watered" else " · needs water"
                },
                style = OllType.body,
                color = colors.ink,
            )
            when {
                crop == null -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Crop.entries.forEach { option ->
                        val affordable = viewModel.camp.storage[Resource.COINS] >= option.seedCoins
                        SecondaryButton(
                            text = option.label,
                            detail = "${option.seedCoins}c",
                            enabled = affordable,
                            onClick = { viewModel.plant(garden, i, option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Gardening.ready(bed) -> ActionRow(
                    title = "Harvest",
                    icon = OllIcons.garden,
                    facts = listOf(Fact("+${crop.yield} ${crop.produce.label}")),
                    onClick = { viewModel.harvest(garden, i) },
                )
            }
        }
    }
}

/** One choice in "Your look": a label and a chip per option, the chosen one filled. */
@Composable
private fun LookRow(label: String, options: List<Pair<String, Int>>, chosen: Int, onChoose: (Int) -> Unit) {
    val colors = OllTheme.colors
    Text(label, style = OllType.caption, color = colors.inkMuted)
    Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (name, value) -> ChoiceChip(name, chosen = value == chosen, onClick = { onChoose(value) }) }
    }
}

/** A piece of furniture inside a room: what it offers. */
@Composable
private fun PieceSheet(selection: Selection.Piece, viewModel: GameViewModel, asleep: Boolean) {
    val colors = OllTheme.colors
    val piece = selection.piece
    Sheet(title = piece.kind.label, onDismiss = viewModel::dismissSelection) {
        if (viewModel.furnishing) {
            ActionRow(title = "Move", icon = OllIcons.go, facts = listOf(Fact("Free")), onClick = { viewModel.startMoving(piece) })
            ActionRow(
                title = "Put away",
                icon = OllIcons.chest,
                facts = listOf(Fact("Gives back ${formatCosts(piece.kind.costs)}")),
                tint = colors.honey,
                onClick = { viewModel.putAway(piece) },
            )
        } else if (asleep) {
            Text("Everyone's asleep. Start the next day first.", style = OllType.body, color = colors.inkMuted)
        } else {
            if (piece.kind == FurnitureKind.BED) SleepHereButton(viewModel)
            val actions = viewModel.actionsFor(piece)
            val main = actions.firstOrNull { viewModel.camp.storage.shortfall(it.costs).isEmpty() }
            actions.forEach { action ->
                val short = viewModel.camp.storage.shortfall(action.costs)
                ActionRow(
                    title = action.name,
                    icon = OllIcons.of(action),
                    facts = factsOf(action),
                    problem = if (short.isEmpty()) null else describeShortfall(short),
                    enabled = short.isEmpty(),
                    primary = action == main && piece.kind != FurnitureKind.BED,
                    tint = tintFor(action),
                    onClick = { viewModel.perform(selection.room, piece, action) },
                )
            }
        }
    }
}

/** Going to bed for the night from the bed itself, same as the bar's Bed. */
@Composable
private fun SleepHereButton(viewModel: GameViewModel) {
    ActionRow(
        title = "Sleep for the night",
        icon = OllIcons.sleep,
        facts = listOf(Fact(viewModel.partner?.let { if (it.character.asleep) "Starts the next day" else "Then wait for ${it.character.name}" } ?: "Starts the next day")),
        tint = OllTheme.colors.rose,
        onClick = viewModel::sleep,
    )
}

private fun comingSoon(kind: ObjectKind): String = "Nothing to do here yet."

@Composable
private fun RoomSheet(room: Room, viewModel: GameViewModel, asleep: Boolean) {
    val colors = OllTheme.colors
    Sheet(title = room.kind.label, onDismiss = viewModel::dismissSelection) {
        if (room.kind == RoomKind.CABIN) {
            Text("A real roof. Nights in here restore all your energy.", style = OllType.body, color = colors.inkMuted)
        }
        if (asleep) {
            Text("Everyone's asleep. Start the next day first.", style = OllType.body, color = colors.inkMuted)
        } else {
            if (room.kind == RoomKind.GARDEN) {
                GardenBeds(room, viewModel)
            } else {
                ActionRow(title = "Go inside", icon = OllIcons.inside, facts = listOf(Fact("See the room")), primary = true, onClick = { viewModel.goInside(room) })
            }
            if (room.kind == RoomKind.CABIN) SleepHereButton(viewModel)
            viewModel.actionsFor(room).forEach { action ->
                ActionRow(title = action.name, icon = OllIcons.of(action), facts = factsOf(action), tint = tintFor(action), onClick = { viewModel.perform(room, action) })
            }
        }
    }
}

/** Tapping empty grass: offer to build a room there, with the outline already shown on the map. */
@Composable
private fun GroundSheet(ground: Selection.Ground, viewModel: GameViewModel, asleep: Boolean) {
    val colors = OllTheme.colors
    val options = viewModel.buildOptions()
    Sheet(title = "Build here?", onDismiss = viewModel::dismissSelection, scrim = false) {
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
                val buildable = options.firstOrNull { viewModel.placement(it, ground.x, ground.y) == Placement.Ok && viewModel.camp.storage.shortfall(it.costs).isEmpty() }
                options.forEach { kind ->
                    val placement = viewModel.placement(kind, ground.x, ground.y)
                    val short = viewModel.camp.storage.shortfall(kind.costs)
                    ActionRow(
                        title = "Build the ${kind.label.lowercase()} here",
                        icon = if (kind == RoomKind.GARDEN) OllIcons.garden else OllIcons.build,
                        facts = factsOf(Building.actionFor(kind)),
                        problem = when {
                            placement != Placement.Ok -> placementProblem(placement)
                            short.isNotEmpty() -> describeShortfall(short)
                            else -> null
                        },
                        enabled = placement == Placement.Ok && short.isEmpty(),
                        primary = kind == buildable,
                        onClick = { viewModel.build(kind, ground.x, ground.y) },
                    )
                }
            }
        }
    }
}

private fun placementProblem(placement: Placement): String = when (placement) {
    Placement.OffPlot -> "Rooms go inside your yard"
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
    Resource.entries.filter { storage[it] > 0 }.forEach { resource ->
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OllIcons.of(resource)?.let { Icon(it, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp)) } ?: Spacer(Modifier.size(20.dp))
            Text(resource.label, style = OllType.body, color = colors.ink, modifier = Modifier.weight(1f))
            Text("${storage[resource]}", style = OllType.stat, color = colors.ink)
        }
    }
}

/** Tapping a person (or Me, or Together, in the bar): who they are, how they're doing, and what you can do. */
@Composable
private fun PersonSheet(playerId: String, viewModel: GameViewModel, onPrivate: () -> Unit = {}) {
    val colors = OllTheme.colors
    val player = viewModel.playerFor(playerId) ?: return
    val person = player.character
    val isMe = playerId == viewModel.myPlayerId
    val word = moodWord(person.needs.mood)
    val low = Need.entries.filter { person.needs[it] < 30 }
    val activity = player.activity
    val status = when {
        person.asleep -> "Asleep"
        activity != null -> activity.label
        else -> "Up · ${formatTime(person.clock.minute)}"
    }

    Sheet(title = person.name, note = if (isMe) "You · $status" else status, onDismiss = viewModel::dismissSelection) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                word,
                fill = when (word) {
                    "Cheerful" -> colors.leaf
                    "Fine" -> colors.honey
                    else -> colors.surfaceSunken
                },
            )
            if (low.isNotEmpty()) {
                val who = if (isMe) "You need" else "${person.name} needs"
                Text("LOW", style = OllType.tag, color = colors.berry)
                Text(
                    "$who ${low.joinToString(" and ") { it.label.lowercase() }}." + if (!isMe) " A hand would help." else "",
                    style = OllType.body,
                    color = colors.ink,
                )
            }
        }

        if (!isMe) {
            Section("Say hello")
            Gesture.entries.forEach { gesture ->
                val blocked = viewModel.gestureProblem(gesture)
                ActionRow(
                    title = gesture.label,
                    icon = OllIcons.of(gesture),
                    facts = listOf(Fact(if (viewModel.gestureUsedToday(gesture)) "Again, just because" else "+${gesture.social} Social", OllIcons.of(Need.SOCIAL))),
                    problem = blocked,
                    enabled = blocked == null,
                    tint = colors.sky,
                    onClick = { viewModel.greet(gesture) },
                )
            }
            val problem = viewModel.walkProblem()
            when {
                viewModel.walkInviteFrom?.id == playerId -> PrimaryButton(
                    text = "Go for a walk",
                    detail = "1h · +12 Fun · +${Walks.TOGETHER_SOCIAL} Social",
                    onClick = { viewModel.dismissSelection(); viewModel.acceptWalk() },
                    modifier = Modifier.fillMaxWidth(),
                )
                viewModel.askedForWalk -> SecondaryButton(
                    text = "Call off the walk",
                    onClick = { viewModel.dismissSelection(); viewModel.declineWalk() },
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> PrimaryButton(
                    text = "Ask ${person.name} for a walk",
                    detail = problem ?: "1h · +12 Fun · +${Walks.TOGETHER_SOCIAL} Social",
                    enabled = problem == null,
                    onClick = viewModel::askForWalk,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (!isMe && viewModel.privateOffered) {
            ActionRow(
                title = "Private",
                icon = Icons.Rounded.Lock,
                facts = listOf(Fact("Just the two of you")),
                tint = colors.rose,
                onClick = { viewModel.dismissSelection(); onPrivate() },
            )
        }

        Section("Needs")
        Need.entries.forEach { need -> NeedBar(label = need.label, value = person.needs[need], icon = OllIcons.of(need)) }
        Text("Skills arrive with careers.", style = OllType.caption, color = colors.inkMuted)

        if (isMe) {
            val look = viewModel.lookFor(playerId)
            Section("Your look")
            Text("How you appear in the 3D view, to both of you.", style = OllType.caption, color = colors.inkMuted)
            LookRow("Body", listOf("Body 1" to 0, "Body 2" to 1), look.body) { viewModel.chooseLook(look.copy(body = it)) }
            val outfits = if (look.body == 1) listOf("Casual", "Formal", "Worker") else listOf("Hoodie", "Casual", "Farmer")
            LookRow("Outfit", outfits.mapIndexed { i, name -> name to i }, look.outfit) { viewModel.chooseLook(look.copy(outfit = it)) }
            // Skin tones in order from deepest to lightest (stored as deep 0, brown 2, tan 3, light 1).
            LookRow("Skin", listOf("Deep" to 0, "Brown" to 2, "Tan" to 3, "Light" to 1), look.skin) { viewModel.chooseLook(look.copy(skin = it)) }
        }
    }
}

/** The gear: how this phone shows the game, this world's code, and the way back to the lobby. */
@Composable
private fun SettingsSheet(viewModel: GameViewModel, onDismiss: () -> Unit, onBackToLobby: () -> Unit) {
    val colors = OllTheme.colors
    Sheet(title = "Settings", onDismiss = onDismiss) {
        SettingSwitch(
            title = "3D world",
            detail = if (viewModel.view3d) "Livelier. Turn off on older phones" else "Off: the lighter flat map",
            icon = if (viewModel.view3d) Icons.Rounded.ViewInAr else Icons.Rounded.Map,
            checked = viewModel.view3d,
            onChange = viewModel::chooseView3d,
        )
        if (!viewModel.isSolo) {
            SettingSwitch(
                title = "Camp news",
                detail = "Notifications when the game is closed",
                icon = Icons.Rounded.Notifications,
                checked = viewModel.campNews,
                onChange = viewModel::chooseCampNews,
            )
        }
        if (viewModel.isNeighbours) {
            SettingSwitch(
                title = "Help yourself",
                detail = "Your neighbour can gather on your lot",
                icon = Icons.Rounded.VolunteerActivism,
                checked = viewModel.helpYourself,
                onChange = viewModel::chooseHelpYourself,
            )
        }

        Section("This world")
        if (!viewModel.isSolo) {
            Text("What you two are", style = OllType.caption, color = colors.inkMuted)
            Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.ourlittlelife.rules.Mode.entries.forEach { option ->
                    ChoiceChip(option.label, chosen = option == viewModel.mode, onClick = { viewModel.chooseMode(option) })
                }
            }
        }
        Text(
            if (viewModel.isSolo) {
                "Your solo game, saved on this phone. To play with someone, start or join a shared world from the lobby."
            } else {
                "Code ${viewModel.worldCode}. Share it so a friend can join" +
                    (viewModel.partnerName?.let { ". You're playing with $it." } ?: ".")
            },
            style = OllType.body,
            color = colors.inkMuted,
        )
        SecondaryButton(
            text = "Back to lobby",
            detail = "Switch worlds or join a friend",
            onClick = { onDismiss(); onBackToLobby() },
            modifier = Modifier.fillMaxWidth(),
        )
        if (BuildConfig.DEBUG) {
            SecondaryButton(
                text = "Debug: fill the chest",
                detail = "Test builds only",
                onClick = { viewModel.debugFillChest(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Together, before anyone has joined: the code to send a friend. */
@Composable
private fun InviteSheet(viewModel: GameViewModel, onDismiss: () -> Unit) {
    val colors = OllTheme.colors
    val context = LocalContext.current
    Sheet(title = "Together", onDismiss = onDismiss) {
        if (viewModel.isSolo) {
            Text("This is your solo game. To play with someone, start or join a shared world from the lobby (Settings, then Back to lobby).", style = OllType.body, color = colors.inkMuted)
        } else {
            Text("Nobody has joined yet. Send a friend this code; they type it in under Join a friend.", style = OllType.body, color = colors.inkMuted)
            Text(
                viewModel.worldCode,
                style = OllType.display,
                color = colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceSunken)
                    .padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            PrimaryButton(
                text = "Send the code",
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "Join my world in Our Little Life. The code is ${viewModel.worldCode}.")
                    context.startActivity(android.content.Intent.createChooser(send, null))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Bed, from the bar: a moment to mean it, since the day ends for you once you sleep. */
@Composable
private fun BedSheet(viewModel: GameViewModel, onDismiss: () -> Unit) {
    val colors = OllTheme.colors
    val partner = viewModel.partner
    val tomorrow = Clock.startOfDay(viewModel.character.clock.day + 1).weekday
    Sheet(title = "Go to bed?", onDismiss = onDismiss) {
        Text(
            when {
                partner == null -> "$tomorrow starts as soon as you're asleep."
                partner.character.asleep -> "${partner.character.name} is already asleep, so $tomorrow starts right away."
                else -> "Your day ends here. $tomorrow starts once ${partner.character.name} is asleep too."
            },
            style = OllType.body,
            color = colors.inkMuted,
        )
        PrimaryButton(
            text = "Go to bed",
            detail = if (viewModel.character.clock.late) "It's late" else null,
            onClick = { onDismiss(); viewModel.sleep() },
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(text = "Stay up", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
