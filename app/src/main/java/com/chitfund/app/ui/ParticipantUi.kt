package com.chitfund.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitfund.app.*
import kotlin.math.abs

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)

@Composable
fun ParticipantContent(vm: ChitViewModel, chit: Chit) {
    val auctions by vm.auctions(chit.id).collectAsState(emptyList())
    val bids = auctions.associate { it.monthNo to it.bid }
    var editMonth by remember { mutableStateOf<Int?>(null) }
    var editWin by remember { mutableStateOf(false) }

    val value = ChitMath.chitValue(chit)
    val commission = ChitMath.commission(value, chit.commissionPercent)
    val assumedLast = chit.myWonMonth == null
    val result = ChitMath.analyse(chit, bids, chit.myWonMonth ?: chit.members)
    val nextMonth = (1..chit.members).firstOrNull { it !in bids }
    val curve = (1..chit.members).map { ChitMath.analyse(chit, bids, it).signedAnnualPercent }
    val missing = (1..chit.members).any { it !in bids }
    val maxGain = bids.values.maxOfOrNull { ChitMath.gainPercent(chit.monthlyAmount, chit.members, it) } ?: 0.0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (nextMonth != null) {
            item {
                Button(onClick = { editMonth = nextMonth }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enter month $nextMonth bid")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Chit value ${money(value)}", style = MaterialTheme.typography.titleMedium)
                    Text("Commission ${pct(chit.commissionPercent)} = ${money(commission)} deducted from the winner each month")
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("My winning month: ${chit.myWonMonth ?: "not set"}", fontWeight = FontWeight.Medium)
                        TextButton(onClick = { editWin = true }) { Text("Change") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (assumedLast) {
                        Text(
                            "Assuming you take the money in the last month (month ${chit.members}). Set your winning month above if you win earlier.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    when {
                        result.signedAnnualPercent == null -> {
                            Text("No single yearly rate fits this pattern.", style = MaterialTheme.typography.titleMedium)
                            Text("Over the chit you pay ${money(result.totalPaid)} and receive ${money(result.prize)} (difference ${money(result.net)}). This happens when you lend early and borrow later, so one rate cannot describe it.")
                        }
                        else -> {
                            val s = result.signedAnnualPercent
                            Text(
                                if (s >= 0) "You earn about ${pct(abs(s))} a year" else "You pay about ${pct(abs(s))} a year",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (s >= 0) Green else Red
                            )
                            Text(if (s >= 0) "Winning late works like a savings deposit." else "Winning early works like a loan; this is its cost.")
                            Spacer(Modifier.height(8.dp))
                            Text("Total you pay over ${chit.members} months: ${money(result.totalPaid)}")
                            Text("Amount you receive: ${money(result.prize)}")
                            Text(
                                if (result.net >= 0) "Interest earned: ${money(result.net)}" else "Interest paid: ${money(-result.net)}",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (missing) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Estimate: months without a bid reuse the last known bid.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item {
            Text("Yearly interest by winning month", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            RateChart(curve, chit.myWonMonth)
            Text(
                "Green: you earn interest. Red: you pay interest. Your month is bold.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Text("Auction results", style = MaterialTheme.typography.titleMedium)
            Text("Tap a month to enter its bid.", style = MaterialTheme.typography.bodySmall)
        }
        items((1..chit.members).toList()) { m ->
            val a = bids[m]
            val shown = a ?: ChitMath.bidFor(bids, m)
            Card(Modifier.fillMaxWidth().clickable { editMonth = m }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Month $m" + if (chit.myWonMonth == m) " · I win" else "",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "I pay ${money(ChitMath.installment(chit.monthlyAmount, chit.members, shown))}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (a == null) "No bid yet (estimate ${money(shown)})" else "Bid ${money(a)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (chit.myWonMonth == m) {
                            Text("I receive ${money(ChitMath.prize(value, shown, chit.commissionPercent))}")
                        }
                    }
                    if (a != null) {
                        GainChip(ChitMath.gainPercent(chit.monthlyAmount, chit.members, a), maxGain)
                    }
                }
            }
        }
    }

    editMonth?.let { m ->
        BidDialog(
            month = m,
            existing = bids[m],
            onDismiss = { editMonth = null },
            onClear = { vm.deleteAuction(chit.id, m); editMonth = null },
            onSave = { b ->
                val existingId = auctions.find { it.monthNo == m }?.id ?: 0L
                vm.saveAuction(Auction(id = existingId, chitId = chit.id, monthNo = m, bid = b.coerceIn(0.0, value)))
                editMonth = null
            }
        )
    }
    if (editWin) {
        WinDialog(
            current = chit.myWonMonth,
            max = chit.members,
            onDismiss = { editWin = false },
            onSave = { vm.updateChit(chit.copy(myWonMonth = it)); editWin = false }
        )
    }
}

@Composable
private fun BidDialog(month: Int, existing: Double?, onDismiss: () -> Unit, onClear: () -> Unit, onSave: (Double) -> Unit) {
    var bid by remember { mutableStateOf(existing?.toLong()?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Month $month bid") },
        text = { NumField("Bid amount (discount, ₹)", bid) { bid = it } },
        confirmButton = {
            TextButton(enabled = bid.toDoubleOrNull() != null, onClick = { onSave(bid.toDouble()) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun WinDialog(current: Int?, max: Int, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var v by remember { mutableStateOf(current?.toString() ?: "") }
    val parsed = v.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My winning month") },
        text = { NumField("Month (1 to $max, blank to clear)", v, decimal = false) { v = it } },
        confirmButton = {
            TextButton(
                enabled = v.isBlank() || (parsed != null && parsed in 1..max),
                onClick = { onSave(parsed) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RateChart(values: List<Double?>, highlight: Int?) {
    val maxAbs = values.filterNotNull().maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val n = values.size
        val w = size.width / n
        val mid = size.height / 2f
        drawLine(Color.Gray, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
        values.forEachIndexed { i, v ->
            if (v != null) {
                val h = (abs(v) / maxAbs).toFloat() * (mid - 8f)
                val top = if (v >= 0) mid - h else mid
                val base = if (v >= 0) Green else Red
                val color = if (highlight == null || highlight == i + 1) base else base.copy(alpha = 0.45f)
                drawRect(color, Offset(i * w + w * 0.15f, top), Size(w * 0.7f, h))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        values.indices.forEach { i ->
            Text(
                "${i + 1}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 9.sp,
                fontWeight = if (highlight == i + 1) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
