package com.ourlittlelife.game.home

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.ourlittlelife.game.privacy.PinLock
import com.ourlittlelife.game.ui.components.ActionRow
import com.ourlittlelife.game.ui.components.Fact
import com.ourlittlelife.game.ui.components.OllIcons
import com.ourlittlelife.game.ui.components.PopCard
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.components.SettingSwitch
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import com.ourlittlelife.game.day.formatDuration
import com.ourlittlelife.rules.Moment
import com.ourlittlelife.rules.MomentProblem
import com.ourlittlelife.rules.Private

/**
 * The private side of a Partners world, behind this phone's PIN. While it's open the screen can't be
 * captured or shown in the app switcher, and it closes itself when the game leaves the screen.
 * Nothing in here is drawn: a moment is asked for, accepted, and then time simply passes.
 */
@Composable
fun PrivateSpace(viewModel: GameViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }

    // Leaving the game locks it again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) onClose() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // No screenshots, no recording, no preview in the app switcher: on this window and the one behind it.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val activityWindow = (context as? Activity)?.window
        DisposableEffect(dialogWindow, activityWindow) {
            val secure = WindowManager.LayoutParams.FLAG_SECURE
            dialogWindow?.setFlags(secure, secure)
            activityWindow?.addFlags(secure)
            onDispose { activityWindow?.clearFlags(secure) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OllTheme.colors.surface)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(onClose)
            if (!unlocked) {
                Gate(
                    onUnlocked = { unlocked = true },
                    onReset = {
                        PinLock.clear(context)
                        viewModel.resetPrivate()
                    },
                )
            } else {
                Inside(viewModel)
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    val colors = OllTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(colors.rose), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.onFill, modifier = Modifier.size(22.dp))
        }
        Text("Private", style = OllType.title, color = colors.ink, modifier = Modifier.padding(start = 12.dp).weight(1f).semantics { heading() })
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Close", onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.ink, modifier = Modifier.size(24.dp))
        }
    }
}

/** Choose a PIN the first time; enter it every time after. */
@Composable
private fun Gate(onUnlocked: () -> Unit, onReset: () -> Unit) {
    val colors = OllTheme.colors
    val context = LocalContext.current
    var hasPin by remember { mutableStateOf(PinLock.isSet(context)) }
    var pin by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    @Composable
    fun PinField(value: String, label: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed -> onChange(typed.filter(Char::isDigit).take(PinLock.MAX_LENGTH)); wrong = false },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = OllType.stat,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (!hasPin) {
        Text(
            "This part of the game is just for the two of you. Choose a PIN for this phone. You'll need it every time, and it's never shared, not even with your partner.",
            style = OllType.body,
            color = colors.inkMuted,
        )
        PinField(pin, "PIN, ${PinLock.MIN_LENGTH} to ${PinLock.MAX_LENGTH} digits") { pin = it }
        PinField(again, "Same PIN again") { again = it }
        val ready = pin.length >= PinLock.MIN_LENGTH && pin == again
        PrimaryButton(
            text = "Set PIN",
            detail = when {
                pin.length < PinLock.MIN_LENGTH -> "At least ${PinLock.MIN_LENGTH} digits"
                pin != again -> "Type it twice, the same"
                else -> null
            },
            enabled = ready,
            onClick = {
                PinLock.set(context, pin)
                hasPin = true
                onUnlocked()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text("Enter this phone's PIN.", style = OllType.body, color = colors.inkMuted)
        PinField(pin, "PIN") { pin = it }
        if (wrong) Text("That's not it. Try again.", style = OllType.body, color = colors.berry)
        PrimaryButton(
            text = "Unlock",
            enabled = pin.length >= PinLock.MIN_LENGTH,
            onClick = { if (PinLock.matches(context, pin)) onUnlocked() else { wrong = true; pin = "" } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!confirmReset) {
            SecondaryButton(text = "Forgot the PIN", onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "Resetting the PIN turns the private side off for both of you. You'll each have to turn it on again, so your partner will know.",
                style = OllType.body,
                color = colors.ink,
            )
            SecondaryButton(
                text = "Reset and turn it off for both",
                onClick = {
                    onReset()
                    hasPin = false
                    pin = ""
                    again = ""
                    confirmReset = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Inside(viewModel: GameViewModel) {
    val colors = OllTheme.colors
    val partner = viewModel.partnerName ?: "your partner"

    @Composable
    fun Section(text: String) =
        Text(text, style = OllType.heading, color = colors.ink, modifier = Modifier.padding(top = 8.dp).semantics { heading() })

    if (!viewModel.privateOn) {
        Text(
            "Closer moments and notes for the two of you, on your phones only. It stays off until you've both said yes, each on your own phone, and either of you can turn it off for both at any time.",
            style = OllType.body,
            color = colors.inkMuted,
        )
        SettingSwitch(
            title = "I'm in",
            detail = when {
                !viewModel.myAdult -> "Your own yes. $partner gives theirs on their phone"
                viewModel.partnerAdult -> "You're both in"
                else -> "Waiting for $partner to say yes on their phone"
            },
            icon = Icons.Rounded.Favorite,
            checked = viewModel.myAdult,
            onChange = viewModel::choosePrivate,
        )
        return
    }

    // A moment that just happened: no picture, just the time passing.
    viewModel.momentShared?.let { moment ->
        PopCard(modifier = Modifier.fillMaxWidth(), color = colors.rose) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(moment.label, style = OllType.title, color = colors.onFill)
                Text("${formatDuration(moment.minutes)} pass, just the two of you. You both feel closer.", style = OllType.body, color = colors.onFill)
                SecondaryButton(text = "Lovely", onClick = viewModel::clearMoment)
            }
        }
    }

    Section("Your bond")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(colors.surfaceSunken)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(viewModel.bond / Private.BOND_MAX.toFloat()).clip(RoundedCornerShape(50)).background(colors.rose))
        }
        Text("${viewModel.bond} of ${Private.BOND_MAX}", style = OllType.stat, color = colors.ink)
    }

    val invite = viewModel.momentInvite
    val asking = viewModel.askingMoment
    Section("Moments")
    when {
        invite != null -> PopCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${invite.first.character.name} is asking: ${invite.second.label.lowercase()}?", style = OllType.body, color = colors.ink)
                PrimaryButton(text = "Yes", detail = formatDuration(invite.second.minutes), onClick = viewModel::acceptMoment, modifier = Modifier.fillMaxWidth())
                SecondaryButton(text = "Not now", onClick = viewModel::declineMoment, modifier = Modifier.fillMaxWidth())
            }
        }
        asking != null -> PopCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You asked $partner: ${asking.label.lowercase()}? Waiting for their answer.", style = OllType.body, color = colors.ink)
                SecondaryButton(text = "Take it back", onClick = viewModel::declineMoment, modifier = Modifier.fillMaxWidth())
            }
        }
        else -> Moment.entries.forEach { moment ->
            val problem = viewModel.momentProblem(moment)
            ActionRow(
                title = moment.label,
                icon = Icons.Rounded.Favorite,
                facts = listOf(Fact(formatDuration(moment.minutes), OllIcons.time), Fact("$partner has to say yes")),
                problem = when (problem) {
                    null -> null
                    MomentProblem.OFF -> "The private side is off"
                    MomentProblem.ASLEEP -> "One of you is asleep"
                    MomentProblem.APART -> "Stand side by side, or be in the same room"
                    MomentProblem.BOND -> "Grows with your bond: needs ${moment.needsBond}"
                    MomentProblem.PLACE -> if (moment == Moment.SHOWER) "Needs you both in the bathroom" else "Needs you both in a room with a bed"
                },
                enabled = problem == null,
                tint = colors.rose,
                onScreen = true,
                onClick = { viewModel.askMoment(moment) },
            )
        }
    }

    Section("Notes")
    Text("Only the two of you can read these. They live in your world, not in the app.", style = OllType.caption, color = colors.inkMuted)
    var draft by rememberSaveable { mutableStateOf("") }
    viewModel.notes.forEach { note ->
        val mine = note.from == viewModel.myPlayerId
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (mine) colors.peach else colors.surfaceRaised).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (mine) "You" else partner, style = OllType.caption, color = if (mine) colors.onFill else colors.inkMuted)
                Text(note.text, style = OllType.body, color = if (mine) colors.onFill else colors.ink)
            }
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Delete for both of you") { viewModel.deleteNote(note.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete this note for both of you", tint = if (mine) colors.onFill else colors.inkMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it.take(Private.NOTE_LENGTH) },
        label = { Text("A note for $partner") },
        textStyle = OllType.body,
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(
        text = "Leave the note",
        enabled = draft.isNotBlank(),
        onClick = { viewModel.leaveNote(draft); draft = "" },
        modifier = Modifier.fillMaxWidth(),
    )

    Section("This side of the game")
    SettingSwitch(
        title = "Private side",
        detail = "Turning it off turns it off for both of you",
        icon = Icons.Rounded.Lock,
        checked = viewModel.myAdult,
        onChange = viewModel::choosePrivate,
    )
    Spacer(Modifier.height(16.dp))
}
