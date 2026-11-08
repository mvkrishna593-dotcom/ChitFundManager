package com.chitfund.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chitfund.app.*

/** Form the manager fills to create a chit he runs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerChitForm(vm: ChitViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    var comm by remember { mutableStateOf("5") }
    var start by remember { mutableStateOf<Long?>(null) }
    var pick by remember { mutableStateOf(false) }

    val amt = monthly.toDoubleOrNull()
    val m = count.toIntOrNull()
    val tot = total.toDoubleOrNull()
    val cp = comm.toDoubleOrNull()
    val mismatch = tot != null && amt != null && m != null && kotlin.math.abs(tot - amt * m) > 0.5
    val valid = name.isNotBlank() && amt != null && amt > 0 && m != null && m > 1 &&
        tot != null && !mismatch && cp != null && cp in 0.0..100.0 && start != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New chit", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackIcon(onDone) },
                colors = headerColors()
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Field("Chit name", name) { name = it }
            NumField("Monthly chit amount (₹)", monthly) {
                monthly = it
                val a = it.toDoubleOrNull(); val mm = count.toIntOrNull()
                if (a != null && mm != null) total = (a * mm).toLong().toString()
            }
            NumField("Number of chits (members)", count, decimal = false) {
                count = it
                val a = monthly.toDoubleOrNull(); val mm = it.toIntOrNull()
                if (a != null && mm != null) total = (a * mm).toLong().toString()
            }
            NumField("Total chit amount (₹)", total) { total = it }
            if (mismatch) {
                Text(
                    "Total should be monthly amount × number of chits",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            DateButton("Starting date", start) { pick = true }
            NumField("Your commission (%)", comm) { comm = it }
            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    vm.addChit(
                        Chit(
                            name = name.trim(), role = "ORGANISER", members = m!!,
                            monthlyAmount = amt!!, commissionPercent = cp!!,
                            startMillis = start,
                            endMillis = start?.let { Dates.addMonths(it, m - 1) }
                        )
                    )
                    onDone()
                }
            ) { Text("Save chit") }
            Text("Next, open the chit and add your members.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (pick) DatePickDialog(start, { pick = false }) { start = it; pick = false }
}

/** Manager's Home card: this month's collection and the commission earned. */
@Composable
fun ManagerChitCard(vm: ChitViewModel, c: Chit, onClick: () -> Unit) {
    val members by vm.members(c.id).collectAsState(emptyList())
    val auctions by vm.auctions(c.id).collectAsState(emptyList())
    val payments by vm.payments(c.id).collectAsState(emptyList())
    val month = Dates.running(c, auctions.map { it.monthNo })
    val bid = auctions.find { it.monthNo == month }?.bid
    val due = bid?.let { ChitMath.installment(c.monthlyAmount, c.members, it) }
    val paidCount = payments.count { it.monthNo == month && it.paid && members.any { m -> m.id == it.memberId } }
    val commission = ChitMath.commission(ChitMath.chitValue(c), c.commissionPercent) * auctions.size

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Month $month/${c.members} · ${members.size}/${c.members} members · ${money(ChitMath.chitValue(c))}",
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (due == null) {
                Text("Auction for month $month not entered yet")
            } else {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Column {
                        Text("Collected", style = MaterialTheme.typography.bodySmall)
                        Text(money(paidCount * due), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Pending", style = MaterialTheme.typography.bodySmall)
                        Text(money((members.size - paidCount).coerceAtLeast(0) * due), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Commission earned", style = MaterialTheme.typography.bodySmall)
                        Text(money(commission), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun openWhatsApp(ctx: Context, phone: String, text: String) {
    val digits = phone.filter { it.isDigit() }
    val number = if (digits.length == 10) "91$digits" else digits
    val uri = Uri.parse("https://wa.me/$number?text=" + Uri.encode(text))
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Could not open WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

/** Send the amount-to-pay message to each member on WhatsApp, one tap each. */
@Composable
fun MessagesTab(chit: Chit, members: List<Member>, auctions: List<Auction>, payments: List<Payment>) {
    val ctx = LocalContext.current
    var month by rememberSaveable { mutableIntStateOf(Dates.running(chit, auctions.map { it.monthNo })) }
    var unpaidOnly by rememberSaveable { mutableStateOf(false) }
    var sent by remember { mutableStateOf(setOf<Long>()) }

    val auction = auctions.find { it.monthNo == month }
    val due = auction?.let { ChitMath.installment(chit.monthlyAmount, chit.members, it.bid) }
    val paidIds = payments.filter { it.monthNo == month && it.paid }.map { it.memberId }.toSet()
    val shown = if (unpaidOnly) members.filter { it.id !in paidIds } else members
    val monthText = Dates.monthDate(chit, month)?.let { Dates.monthYear(it) } ?: "month $month"

    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(enabled = month > 1, onClick = { month--; sent = emptySet() }) { Text("◀ Prev") }
            Text("Month $month", style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = month < chit.members, onClick = { month++; sent = emptySet() }) { Text("Next ▶") }
        }
        if (auction == null || due == null) {
            Text(
                "Enter this month's auction first. Then the amount each member pays is worked out and you can send it.",
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }
        Text("Bid ${money(auction.bid)} · each member pays ${money(due)}", fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = unpaidOnly, onCheckedChange = { unpaidOnly = it })
            Spacer(Modifier.width(8.dp))
            Text("Only members who have not paid (reminder)")
        }
        Text(
            "Tap Send. WhatsApp opens with the message ready; press send there.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Medium)
                            Text(
                                if (m.phone.isBlank()) "No WhatsApp number" else m.phone,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (m.id in sent) Text("Opened in WhatsApp ✓", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            enabled = m.phone.isNotBlank(),
                            onClick = {
                                val text = if (unpaidOnly) {
                                    "Hello ${m.name}, a gentle reminder: your chit amount ${money(due)} for ${chit.name} ($monthText) is still pending. Please pay at the earliest. Thank you."
                                } else {
                                    "Hello ${m.name}, ${chit.name} chit, $monthText auction is done (bid ${money(auction.bid)}). Your amount to pay this month is ${money(due)}. Please pay within this week. Thank you."
                                }
                                sent = sent + m.id
                                openWhatsApp(ctx, m.phone, text)
                            }
                        ) { Text("Send") }
                    }
                }
            }
        }
    }
}
