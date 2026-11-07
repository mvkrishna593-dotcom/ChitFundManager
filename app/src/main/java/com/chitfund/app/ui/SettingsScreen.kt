package com.chitfund.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chitfund.app.Biometrics
import com.chitfund.app.SecurityViewModel
import com.chitfund.app.findFragmentActivity

@Composable
fun SettingsScreen(
    vm: SecurityViewModel,
    onBack: () -> Unit,
    onSetupPin: () -> Unit,
    onChangePin: () -> Unit,
    onChangeQuestion: () -> Unit
) {
    val ctx = LocalContext.current
    val lockOn by vm.lockEnabled.collectAsState()
    val bioOn by vm.biometricOn.collectAsState()
    val hasPin by vm.hasPin.collectAsState()
    val bioAvailable = Biometrics.available(ctx)
    var verify by remember { mutableStateOf<(() -> Unit)?>(null) }

    SimplePage("App Settings", onBack) {
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Security", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                SettingRow(
                    title = "App lock",
                    subtitle = "Ask for a 4-digit PIN every time the app opens",
                    checked = lockOn,
                    enabled = true
                ) { on ->
                    if (on) {
                        if (hasPin) vm.setLockEnabled(true) else onSetupPin()
                    } else {
                        verify = { vm.setLockEnabled(false) }
                    }
                }

                if (lockOn) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ClickRow("Change PIN", "Set a new 4-digit PIN", onChangePin)

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SettingRow(
                        title = "Fingerprint login",
                        subtitle = if (bioAvailable) "Open the app with your fingerprint or face"
                        else "Not set up on this phone",
                        checked = bioOn,
                        enabled = bioAvailable
                    ) { on ->
                        if (!on) {
                            vm.setBiometric(false)
                        } else {
                            val act = ctx.findFragmentActivity()
                            if (act != null) {
                                Biometrics.prompt(act, "Confirm to turn on") { vm.setBiometric(true) }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ClickRow("Security question", "Used to reset a forgotten PIN") {
                        verify = onChangeQuestion
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Saving your data to your Google Drive will be added in a later update.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    verify?.let { action ->
        VerifyPinDialog(
            vm,
            onDismiss = { verify = null },
            onVerified = {
                verify = null
                action()
                if (!vm.lockEnabled.value) {
                    Toast.makeText(ctx, "App lock turned off", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}
