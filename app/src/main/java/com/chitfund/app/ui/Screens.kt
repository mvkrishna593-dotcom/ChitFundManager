package com.chitfund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chitfund.app.Chit
import com.chitfund.app.ChitViewModel
import java.util.Locale

fun money(d: Double): String = "₹" + String.format(Locale("en", "IN"), "%,.0f", d)
fun pct(d: Double): String = String.format(Locale.US, "%.1f%%", d)

@Composable
fun NumField(
    label: String,
    value: String,
    decimal: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChitScreen(vm: ChitViewModel, role: String, onDone: () -> Unit) {
    if (role == "PARTICIPANT") {
        MemberChitForm(vm, onDone)
        return
    }
    if (role == "ORGANISER") {
        ManagerChitForm(vm, onDone)
        return
    }
    var name by remember { mutableStateOf("") }
    var members by remember { mutableStateOf("20") }
    var monthly by remember { mutableStateOf("10000") }
    var comm by remember { mutableStateOf("5") }
    var won by remember { mutableStateOf("") }

    val m = members.toIntOrNull()
    val amt = monthly.toDoubleOrNull()
    val cp = comm.toDoubleOrNull()
    val valid = name.isNotBlank() && m != null && m > 1 && amt != null && amt > 0 && cp != null && cp in 0.0..100.0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (role == "ORGANISER") "New chit (organiser)" else "New chit (member)", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackIcon(onDone) },
                colors = headerColors()
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Chit name") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            NumField("Number of members", members, decimal = false) { members = it }
            NumField("Monthly amount per member (₹)", monthly) { monthly = it }
            NumField("Organiser commission (%)", comm) { comm = it }
            if (role == "PARTICIPANT") {
                NumField("Month I won / plan to win (optional)", won, decimal = false) { won = it }
            }
            Button(
                enabled = valid,
                onClick = {
                    val wm = won.toIntOrNull()?.takeIf { it in 1..(m ?: 0) }
                    vm.addChit(
                        Chit(
                            name = name.trim(), role = role, members = m!!,
                            monthlyAmount = amt!!, commissionPercent = cp!!,
                            myWonMonth = if (role == "PARTICIPANT") wm else null
                        )
                    )
                    onDone()
                }
            ) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChitScreen(vm: ChitViewModel, id: Long, onBack: () -> Unit) {
    val chit by vm.chit(id).collectAsState(null)
    var confirm by remember { mutableStateOf(false) }
    val c = chit

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(c?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackIcon(onBack) },
                actions = { HeaderButton("Delete") { confirm = true } },
                colors = headerColors()
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (c != null) {
                if (c.role == "ORGANISER") OrganiserContent(vm, c) else ParticipantContent(vm, c)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this chit?") },
            text = { Text("All its data on this phone will be removed.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; vm.deleteChit(id); onBack() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}
