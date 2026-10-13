package com.ourlittlelife.game.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ourlittlelife.game.ui.components.PrimaryButton
import com.ourlittlelife.game.ui.components.SecondaryButton
import com.ourlittlelife.game.ui.theme.OllTheme
import com.ourlittlelife.game.ui.theme.OllType
import kotlinx.coroutines.launch

private enum class Stage { OFFER, ALLOW, DOWNLOADING, FAILED }

/**
 * Checks for a newer version when the game opens. If there is one: "Version X is ready", with Update
 * (download, then Android's installer) or Later. The first time, it explains the one-off permission.
 */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = OllTheme.colors
    var release by remember { mutableStateOf<Release?>(null) }
    var stage by remember { mutableStateOf(Stage.OFFER) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { release = Updater.newer() }
    val offered = release ?: return

    fun start() {
        if (!Updater.mayInstall(context)) {
            stage = Stage.ALLOW
            Updater.askToAllowInstalls(context)
            return
        }
        stage = Stage.DOWNLOADING
        scope.launch {
            runCatching { Updater.download(context, offered) { progress = it } }
                .onSuccess { apk ->
                    Updater.install(context, apk)
                    release = null
                }
                .onFailure { stage = Stage.FAILED }
        }
    }

    AlertDialog(
        onDismissRequest = { if (stage != Stage.DOWNLOADING) release = null },
        containerColor = colors.surfaceRaised,
        title = { Text("Version ${offered.versionName} is ready", style = OllType.title, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (stage) {
                        Stage.OFFER -> offered.notes.ifBlank { "A new version of Our Little Life." }
                        Stage.ALLOW -> "Android needs your OK once: allow Our Little Life to install updates, then come back and tap Update."
                        Stage.DOWNLOADING -> "Downloading. Android will ask you to confirm the install."
                        Stage.FAILED -> "The download didn't finish. Check your connection and try again."
                    },
                    style = OllType.body,
                    color = colors.ink,
                )
                if (stage == Stage.DOWNLOADING) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.peach,
                        trackColor = colors.surfaceSunken,
                    )
                    Text("${(progress * 100).toInt()}% downloaded", style = OllType.caption, color = colors.inkMuted)
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = if (stage == Stage.DOWNLOADING) "Downloading…" else "Update",
                enabled = stage != Stage.DOWNLOADING,
                onClick = ::start,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            if (stage != Stage.DOWNLOADING) SecondaryButton(text = "Later", onClick = { release = null }, modifier = Modifier.fillMaxWidth())
        },
    )
}
