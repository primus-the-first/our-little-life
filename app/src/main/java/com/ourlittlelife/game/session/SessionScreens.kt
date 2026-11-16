package com.ourlittlelife.game.session

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Color
import com.ourlittlelife.game.home.PlacedCharacter
import com.ourlittlelife.game.home.PlotCanvas
import com.ourlittlelife.game.home.Sprites
import com.ourlittlelife.game.ui.components.ActionRow
import com.ourlittlelife.game.ui.components.ChoiceCard
import com.ourlittlelife.game.ui.components.Fact
import com.ourlittlelife.game.ui.components.OllIcons
import com.ourlittlelife.game.ui.components.PopCard
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.StarterPlot
import com.ourlittlelife.rules.WorldMap
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.rules.Mode

@Composable
fun LoadingScreen(text: String = "Getting your world ready…") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OllTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OllType.body, color = OllTheme.colors.inkMuted)
    }
}

@Composable
fun SignInScreen(state: Session.SignedOut, onSignIn: (Activity) -> Unit, onPlaySolo: () -> Unit) {
    val colors = OllTheme.colors
    val activity = LocalContext.current as Activity
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.weight(1f))
        CampScene(Modifier.fillMaxWidth().aspectRatio(1.5f))
        Spacer(Modifier.height(8.dp))
        Text("Our Little Life", style = OllType.display, color = colors.ink, modifier = Modifier.semantics { heading() })
        Text(
            "A little world for two. Build a home together, one day at a time, from your own phones.",
            style = OllType.body,
            color = colors.inkMuted,
        )
        Spacer(Modifier.weight(1f))
        state.error?.let { Text(it, style = OllType.body, color = colors.berry) }
        PrimaryButton(
            text = if (state.busy) "Signing in…" else "Continue with Google",
            onClick = { onSignIn(activity) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "We only use your first name, so your friend sees who's who.",
            style = OllType.caption,
            color = colors.inkMuted,
        )
        SecondaryButton(
            text = "Play solo on this phone",
            detail = "No sign-in, works offline",
            onClick = onPlaySolo,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A corner of the world to look at before you're in it: the starter camp, with two people by the fire. */
@Composable
private fun CampScene(modifier: Modifier = Modifier) {
    val colors = OllTheme.colors
    val plot = androidx.compose.runtime.remember { WorldMap.expand(StarterPlot.create()) }
    val fire = plot.objects.first { it.kind == ObjectKind.CAMPFIRE }
    PopCard(modifier = modifier, radius = 20.dp) {
        PlotCanvas(
            plot = plot,
            characters = listOf(
                PlacedCharacter("You", Sprites.jo, fire.x - 1, fire.y, colors.peach),
                PlacedCharacter("A friend", Sprites.maya, fire.x + 1, fire.y, colors.sky),
            ),
            selected = null,
            ghost = null,
            focusTile = fire.x to fire.y,
            follow = false,
            showYard = false,
            onTapTile = { _, _ -> },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Each mode's badge colour, from the design system: Besties honey, Roommates leaf, Partners rose. */
@Composable
private fun modeColor(mode: Mode): Color = when (mode) {
    Mode.BESTIES -> OllTheme.colors.honey
    Mode.ROOMMATES -> OllTheme.colors.leaf
    Mode.PARTNERS -> OllTheme.colors.rose
}

private val modeLines = mapOf(
    Mode.BESTIES to "Gossip, spa days, karaoke, hyping each other up",
    Mode.ROOMMATES to "Pizza nights, splitting chores, house meetings",
    Mode.PARTNERS to "Date nights, cuddles, slow dances",
)

/** The lobby: the worlds you belong to, then starting a new one or joining a friend's. */
@Composable
fun LobbyScreen(
    state: Session.Lobby,
    onOpen: (String) -> Unit,
    onPlaySolo: () -> Unit,
    onCreate: (Mode, Boolean) -> Unit,
    onJoin: (String) -> Unit,
) {
    val colors = OllTheme.colors
    var mode by rememberSaveable { mutableStateOf(Mode.BESTIES) }
    var neighbours by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    val hasWorlds = state.worlds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hi ${state.name}", style = OllType.caption, color = colors.inkMuted)

        Text("Your worlds", style = OllType.display, color = colors.ink, modifier = Modifier.semantics { heading() })
        // The solo game is always here: on this phone, no partner needed, playable offline.
        ActionRow(
            title = "Solo game",
            icon = OllIcons.me,
            facts = listOf(Fact(state.soloSummary ?: "Start playing on your own")),
            onClick = onPlaySolo,
            enabled = !state.busy,
            onScreen = true,
        )

        if (hasWorlds) {
            state.worlds.forEach { world ->
                ActionRow(
                    title = world.partnerName?.let { "With $it" } ?: if (world.needsUpdate) "World ${world.code}" else "Waiting for a friend",
                    icon = OllIcons.together,
                    facts = listOf(
                        Fact(
                            when {
                                world.needsUpdate -> "Needs the latest version"
                                world.partnerName != null -> "${world.mode.label} · Day ${world.day}"
                                else -> "${world.mode.label} · Code ${world.code}"
                            },
                        ),
                    ),
                    tint = modeColor(world.mode),
                    onClick = { onOpen(world.id) },
                    enabled = !state.busy,
                    onScreen = true,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Text(
            if (hasWorlds) "Start a new world" else "Start your world",
            style = if (hasWorlds) OllType.heading else OllType.display,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Pick what you two are. It changes what you can do together.",
            style = OllType.body,
            color = colors.inkMuted,
        )

        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { option ->
                ChoiceCard(
                    title = option.label,
                    detail = modeLines.getValue(option),
                    chosen = option == mode,
                    onClick = { mode = option },
                    badge = modeColor(option),
                    icon = OllIcons.together,
                )
            }
        }

        Text("Where you live", style = OllType.heading, color = colors.ink, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCard(
                title = "Live together",
                detail = "One home and one chest, shared",
                chosen = !neighbours,
                onClick = { neighbours = false },
                badge = colors.peach,
                icon = OllIcons.inside,
            )
            ChoiceCard(
                title = "Next door",
                detail = "A lot, a camp and a chest each. Visit any time",
                chosen = neighbours,
                onClick = { neighbours = true },
                badge = colors.sky,
                icon = OllIcons.nextDoor,
            )
        }

        // With worlds listed above, creating another is a secondary choice; for a first world it's the main one.
        if (hasWorlds) {
            SecondaryButton(
                text = if (state.busy) "Creating…" else "Create a new world",
                onClick = { onCreate(mode, neighbours) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else PrimaryButton(
            text = if (state.busy) "Creating…" else "Create our world",
            onClick = { onCreate(mode, neighbours) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Text("Or join a friend", style = OllType.heading, color = colors.ink, modifier = Modifier.semantics { heading() })
        Text("Type the 6-letter code from their game.", style = OllType.body, color = colors.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { typed -> code = typed.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                label = { Text("World code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = OllType.stat,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "Join",
                onClick = { onJoin(code) },
                enabled = !state.busy && code.length == 6,
            )
        }

        state.error?.let { Text(it, style = OllType.body, color = colors.berry) }
    }
}
