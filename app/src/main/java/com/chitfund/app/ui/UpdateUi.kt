package com.chitfund.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chitfund.app.Updater
import kotlinx.coroutines.launch

/**
 * Checks for a new version when the app opens, and again whenever [manualCheck] increases
 * (the Update button). Shows the dialogs for update available, download progress and messages.
 */
@Composable
fun UpdateHost(manualCheck: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<Updater.Info?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }

    suspend fun check(manual: Boolean) {
        val latest = Updater.latest()
        val current = Updater.currentBuild(ctx)
        if (latest == null) {
            if (manual) message = "Could not check for updates. Please check your internet connection."
        } else if (latest.build > current) {
            info = latest
        } else if (manual) {
            message = "You already have the latest version (build $current)."
        }
    }

    LaunchedEffect(Unit) { check(false) }
    LaunchedEffect(manualCheck) { if (manualCheck > 0) check(true) }

    val available = info
    val pct = progress

    if (available != null && pct == null) {
        AlertDialog(
            onDismissRequest = { info = null },
            title = { Text("Update available") },
            text = { Text("${available.name} is ready to install. Your data stays on the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!Updater.canInstall(ctx)) {
                        message = "Allow “Install unknown apps” for this app on the next screen, then come back and tap Update again."
                        Updater.openInstallSettings(ctx)
                    } else {
                        progress = 0
                        scope.launch {
                            val file = Updater.download(ctx, available.url) { progress = it }
                            progress = null
                            if (file == null) {
                                message = "Download failed. Please try again."
                            } else {
                                info = null
                                Updater.install(ctx, file)
                            }
                        }
                    }
                }) { Text("Update now") }
            },
            dismissButton = { TextButton(onClick = { info = null }) { Text("Later") } }
        )
    }

    if (pct != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading update") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("$pct%")
                }
            },
            confirmButton = {}
        )
    }

    val msg = message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}
