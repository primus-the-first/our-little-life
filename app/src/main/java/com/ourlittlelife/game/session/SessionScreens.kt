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
import com.ourlittlelife.game.ui.components.PrimaryButton
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
    onCreate: (Mode) -> Unit,
    onJoin: (String) -> Unit,
) {
    val colors = OllTheme.colors
    var mode by rememberSaveable { mutableStateOf(Mode.BESTIES) }
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
        SecondaryButton(
            text = "Solo game",
            detail = state.soloSummary ?: "Start playing on your own",
            onClick = onPlaySolo,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )

        if (hasWorlds) {
            state.worlds.forEach { world ->
                SecondaryButton(
                    text = world.partnerName?.let { "With $it" } ?: if (world.needsUpdate) "World ${world.code}" else "Waiting for a friend",
                    detail = when {
                        world.needsUpdate -> "Needs the latest version"
                        world.partnerName != null -> "${world.mode.label} · Day ${world.day}"
                        else -> "Code ${world.code}"
                    },
                    onClick = { onOpen(world.id) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
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

        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Mode.entries.forEach { option ->
                val selected = option == mode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceRaised)
                        .border(2.dp, if (selected) colors.ink else colors.line, RoundedCornerShape(12.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { mode = option })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.label, style = OllType.heading, color = colors.ink)
                        if (selected) Text("Chosen", style = OllType.tag, color = colors.ink)
                    }
                    Text(modeLines.getValue(option), style = OllType.body, color = colors.inkMuted)
                }
            }
        }

        // With worlds listed above, creating another is a secondary choice; for a first world it's the main one.
        if (hasWorlds) {
            SecondaryButton(
                text = if (state.busy) "Creating…" else "Create a new world",
                onClick = { onCreate(mode) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else PrimaryButton(
            text = if (state.busy) "Creating…" else "Create our world",
            onClick = { onCreate(mode) },
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
