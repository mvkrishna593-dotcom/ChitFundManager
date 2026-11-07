package com.chitfund.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chitfund.app.Chit
import com.chitfund.app.ChitViewModel
import com.chitfund.app.Dates

/** Form a member fills to add a chit he has joined. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberChitForm(vm: ChitViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var organiser by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    var comm by remember { mutableStateOf("5") }
    var start by remember { mutableStateOf<Long?>(null) }
    var end by remember { mutableStateOf<Long?>(null) }
    var endEdited by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }

    val amt = monthly.toDoubleOrNull()
    val m = months.toIntOrNull()
    val tot = total.toDoubleOrNull()
    val cp = comm.toDoubleOrNull()
    val totalMismatch = tot != null && amt != null && m != null && abs(tot - amt * m) > 0.5
    val valid = name.isNotBlank() && organiser.isNotBlank() &&
        amt != null && amt > 0 && m != null && m > 1 &&
        tot != null && !totalMismatch &&
        cp != null && cp in 0.0..100.0 &&
        start != null && (end == null || end!! >= start!!)

    // fill in what can be worked out
    fun autoEnd(monthCount: Int?) {
        val s = start
        if (!endEdited && s != null && monthCount != null && monthCount > 1) {
            end = Dates.addMonths(s, monthCount - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add chit", fontWeight = FontWeight.Bold) },
                navigationIcon = { HeaderButton("Back", onDone) },
                colors = headerColors()
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field("Chit name (to identify it)", name) { name = it }
            Field("Organiser name", organiser) { organiser = it }
            OutlinedTextField(
                value = mobile,
                onValueChange = { v -> if (v.length <= 15 && v.all { it.isDigit() || it == '+' }) mobile = v },
                label = { Text("Organiser mobile number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Field("Place", place) { place = it }
            NumField("Monthly chit amount (₹)", monthly) {
                monthly = it
                val a = it.toDoubleOrNull(); val mm = months.toIntOrNull()
                if (a != null && mm != null && total.isBlank()) total = (a * mm).toLong().toString()
            }
            NumField("Number of months", months, decimal = false) {
                months = it
                val a = monthly.toDoubleOrNull(); val mm = it.toIntOrNull()
                if (a != null && mm != null) total = (a * mm).toLong().toString()
                autoEnd(mm)
            }
            OutlinedTextField(
                value = total,
                onValueChange = { total = it },
                label = { Text("Total chit value (₹)") },
                singleLine = true,
                isError = totalMismatch,
                supportingText = {
                    if (totalMismatch) Text("Total should be monthly amount × months")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            DateButton("Chit starting date", start) { picking = "start" }
            DateButton("Chit ending date", end) { picking = "end" }
            NumField("Organiser commission (%)", comm) { comm = it }
            Text(
                "Commission is deducted from the person who takes the chit. Ask your organiser if you are not sure.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.addChit(
                        Chit(
                            name = name.trim(), role = "PARTICIPANT", members = m!!,
                            monthlyAmount = amt!!, commissionPercent = cp!!,
                            organiserName = organiser.trim(), organiserMobile = mobile,
                            place = place.trim(), startMillis = start, endMillis = end
                        )
                    )
                    onDone()
                }
            ) { Text("Save chit") }
        }
    }

    picking?.let { which ->
        val state = rememberDatePickerState(initialSelectedDateMillis = if (which == "start") start else end)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        if (which == "start") { start = it; autoEnd(m) } else { end = it; endEdited = true }
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}

private fun abs(d: Double) = if (d < 0) -d else d

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DateButton(label: String, value: Long?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$label: " + (value?.let { Dates.format(it) } ?: "choose date"))
    }
}
