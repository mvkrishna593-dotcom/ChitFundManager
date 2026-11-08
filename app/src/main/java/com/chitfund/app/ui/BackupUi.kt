package com.chitfund.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chitfund.app.BackupViewModel
import com.chitfund.app.DriveApi
import com.google.android.gms.auth.api.identity.Identity

/** Backup and restore through the person's own Google Drive. */
@Composable
fun BackupCard(vm: BackupViewModel) {
    val ctx = LocalContext.current
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val auto by vm.auto.collectAsState()
    var action by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }

    fun proceed(token: String) {
        if (action == "restore") vm.restore(token) else vm.backup(token)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try {
            val data = r.data ?: throw IllegalStateException("cancelled")
            val res = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)
            val token = res.accessToken
            if (token != null) proceed(token) else vm.fail("Google sign-in was cancelled")
        } catch (e: Exception) {
            vm.fail("Google sign-in was cancelled")
        }
    }

    fun authorize(what: String) {
        action = what
        Identity.getAuthorizationClient(ctx).authorize(DriveApi.request())
            .addOnSuccessListener { res ->
                val token = res.accessToken
                val pi = res.pendingIntent
                if (res.hasResolution() && pi != null) {
                    launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else if (token != null) {
                    proceed(token)
                } else vm.fail("Could not sign in to Google")
            }
            .addOnFailureListener { vm.fail("Could not sign in to Google: ${it.message ?: ""}") }
    }

    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Drive backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Saves your chits to a private folder in your own Google Drive. Use Restore on a new phone to get them back.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { authorize("backup") }) { Text("Back up now") }
                OutlinedButton(enabled = !busy, onClick = { confirmRestore = true }) { Text("Restore") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Back up automatically")
                    Text("When you open the app, at most every 6 hours", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = auto, onCheckedChange = { vm.setAuto(it) })
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Drive?") },
            text = { Text("This replaces the chits on this phone with the saved backup.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; authorize("restore") }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }
}
