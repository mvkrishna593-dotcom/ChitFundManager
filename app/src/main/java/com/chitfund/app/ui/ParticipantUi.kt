package com.chitfund.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitfund.app.*
import kotlin.math.abs

private val Green = Color(0xFF2E7D32)
private val Red = Color(0xFFC62828)

/** Status of one month for the member: Paid, Due, Bid pending or Upcoming. */
private fun statusOf(chit: Chit, month: Int, hasBid: Boolean, paid: Boolean): String = when {
    paid -> "Paid"
    !hasBid -> "Bid pending"
    chit.startMillis != null && Dates.elapsed(chit) >= month -> "Due"
    else -> "Upcoming"
}

@Composable
private fun StatusPill(status: String, onDark: Boolean = false) {
    val (bg, fg) = when (status) {
        "Paid" -> Color(0xFFD8F3DC) to Color(0xFF14532D)
        "Due" -> Color(0xFFFFE0B2) to Color(0xFF8A4B00)
        else -> Color(0xFFE6EBE8) to Color(0xFF34423A)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(status, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
}

/** Member tab on Home: swipeable slides on top, the chit list below. */
@Composable
fun MemberHome(vm: ChitViewModel, chits: List<Chit>, onOpen: (Long) -> Unit, onEdit: (Long) -> Unit) {
    var deleting by remember { mutableStateOf<Chit?>(null) }
    // chits come newest first; chit numbers follow the order they were added
    val numbered = chits.mapIndexed { i, c -> c to (chits.size - i) }.sortedBy { it.second }
    val pager = rememberPagerState { numbered.size }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 12.dp
                ) { page ->
                    val (c, no) = numbered[page]
                    ChitSlide(vm, c, no) { onOpen(c.id) }
                }
                if (numbered.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(numbered.size) { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (i == pager.currentPage) 9.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(if (i == pager.currentPage) Color(0xFF0F5132) else Color(0xFFB8C7BE))
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "My chits",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(numbered, key = { it.first.id }) { (c, no) ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpen(c.id) }) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Chit $no · ${c.name}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${money(c.members * c.monthlyAmount)} · ${money(c.monthlyAmount)}/month · ${c.members} months",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (c.organiserName.isNotBlank()) {
                            Text(
                                "Organiser: ${c.organiserName}" + if (c.place.isNotBlank()) " · ${c.place}" else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    CardActions({ onEdit(c.id) }) { deleting = c }
                }
            }
        }
    }
    deleting?.let { c ->
        ConfirmDeleteDialog(c.name, { vm.deleteChit(c.id); deleting = null }, { deleting = null })
    }
}

@Composable
private fun ChitSlide(vm: ChitViewModel, c: Chit, number: Int, onClick: () -> Unit) {
    val auctions by vm.auctions(c.id).collectAsState(emptyList())
    val payments by vm.payments(c.id).collectAsState(emptyList())
    val month = Dates.running(c, auctions.map { it.monthNo })
    val bid = auctions.find { it.monthNo == month }?.bid
    val paid = payments.any { it.monthNo == month && it.paid }
    val pay = bid?.let { ChitMath.installment(c.monthlyAmount, c.members, it) }
    val status = statusOf(c, month, bid != null, paid)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ink = if (dark) Color.White else Color(0xFF0F3D28)
    val gradient = if (dark) listOf(Color(0xFF1F5C42), Color(0xFF2C7A57)) else listOf(Color(0xFFA7D3BC), Color(0xFF8FC4A9))
    val dateText = Dates.monthDate(c, month)?.let { Dates.monthYear(it) } ?: "Month $month"

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("$dateText  ·  ${c.name}", color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Month $month/${c.members}", color = ink.copy(alpha = 0.85f), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("Bid amount", color = ink.copy(alpha = 0.75f), fontSize = 12.sp)
            Text(
                bid?.let { money(it) } ?: "Not entered",
                color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (paid) "Amount paid" else "Amount to pay",
                color = ink.copy(alpha = 0.75f), fontSize = 12.sp
            )
            Text(
                pay?.let { money(it) } ?: "—",
                color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.align(Alignment.TopEnd)) { StatusPill(status) }
    }
}

/** Lines that sum up the whole chit for the member who has taken it. Null if no win month is set. */
private fun resultLines(chit: Chit, bids: Map<Int, Double>): List<String>? {
    val win = chit.myWonMonth ?: return null
    val n = chit.members
    val a = ChitMath.analyse(chit, bids, win)
    val value = ChitMath.chitValue(chit)
    val bid = ChitMath.bidFor(bids, win)
    val commission = ChitMath.commission(value, chit.commissionPercent)
    val pctOfReceived = if (a.prize > 0) abs(a.net) / a.prize * 100 else 0.0
    val verdict = if (a.net >= 0) "Gain" else "Loss (interest paid)"
    val lines = mutableListOf(
        "Chit taken in month $win",
        "Amount received: ${money(a.prize)}  (${money(value)} - bid ${money(bid)} - commission ${money(commission)})",
        "Total paid over $n months: ${money(a.totalPaid)}",
        "$verdict: ${money(abs(a.net))} = ${pct(pctOfReceived)} of the amount received"
    )
    a.signedAnnualPercent?.let {
        lines += (if (it >= 0) "Interest earned" else "Interest paid") + ": about ${pct(abs(it))} a year"
    }
    return lines
}

@Composable
fun ParticipantContent(vm: ChitViewModel, chit: Chit) {
    val auctions by vm.auctions(chit.id).collectAsState(emptyList())
    val payments by vm.payments(chit.id).collectAsState(emptyList())
    val bids = auctions.associate { it.monthNo to it.bid }
    val paidMonths = payments.filter { it.paid }.map { it.monthNo }.toSet()
    val paidOn = payments.filter { it.paid }.associate { it.monthNo to it.paidMillis }
    var editMonth by remember { mutableStateOf<Int?>(null) }
    var editWin by remember { mutableStateOf(false) }
    var showInterest by remember { mutableStateOf(false) }

    val n = chit.members
    val value = ChitMath.chitValue(chit)
    val completed = (1..n).all { it in bids && it in paidMonths }
    val remaining = if (bids.isEmpty()) n - Dates.elapsed(chit) else n - Dates.running(chit, bids.keys)
    val profits = (1..n).map { m -> bids[m]?.let { it / n } }
    val maxProfit = profits.filterNotNull().maxOrNull() ?: 0.0
    val maxGain = bids.values.maxOfOrNull { ChitMath.gainPercent(chit.monthlyAmount, n, it) } ?: 0.0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("Organiser", chit.organiserName.ifBlank { "—" })
                    InfoLine("Mobile", chit.organiserMobile.ifBlank { "—" })
                    InfoLine("Place", chit.place.ifBlank { "—" })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    InfoLine("Total amount", money(value))
                    InfoLine("Chit amount", money(chit.monthlyAmount) + " / month")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chit taken", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (chit.myWonMonth != null) "Yes (month ${chit.myWonMonth})" else "No",
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { editWin = true }) { Text("Change") }
                        }
                    }
                    InfoLine("Started", chit.startMillis?.let { Dates.format(it) } ?: "—")
                    InfoLine("Remaining months", "$remaining of $n")
                }
            }
        }

        if (chit.myWonMonth != null) {
            item {
                val win = chit.myWonMonth!!
                val a = ChitMath.analyse(chit, bids, win)
                val value = ChitMath.chitValue(chit)
                val bid = ChitMath.bidFor(bids, win)
                val commission = ChitMath.commission(value, chit.commissionPercent)
                val pctOfReceived = if (a.prize > 0) abs(a.net) / a.prize * 100 else 0.0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (completed) "Chit completed ✓" else "Your result so far",
                            style = MaterialTheme.typography.titleMedium
                        )
                        InfoLine("Chit taken in month", "$win")
                        InfoLine("Amount received", money(a.prize))
                        Text(
                            "${money(value)} − bid ${money(bid)} − commission ${money(commission)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        InfoLine("Total paid (all $n months)", money(a.totalPaid))
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        Text(
                            if (a.net >= 0) "Gain ${money(a.net)} (${pct(pctOfReceived)})"
                            else "Loss ${money(-a.net)} (${pct(pctOfReceived)})",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (a.net >= 0) Green else Red
                        )
                        Text(
                            "Percent is of the amount you received." +
                                if (bids.size < n) " Estimate: months without a bid reuse the last known bid." else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text("Monthly profit", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ProfitChart(profits, maxProfit)
        }

        item {
            if (!showInterest) {
                OutlinedButton(onClick = { showInterest = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("View yearly interest")
                }
            } else {
                InterestSection(chit, bids) { showInterest = false }
            }
        }

        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    var paidTotal = 0.0
                    var dueTotal = 0.0
                    val rows = (1..n).filter { it in bids }.map { mo ->
                        val due = ChitMath.installment(chit.monthlyAmount, n, bids.getValue(mo))
                        dueTotal += due
                        if (mo in paidMonths) paidTotal += due
                        StatementRow(
                            "Month $mo",
                            Dates.monthDate(chit, mo)?.let { Dates.format(it) } ?: "-",
                            money(bids.getValue(mo)),
                            money(due),
                            if (mo in paidMonths) "Paid " + (paidOn[mo]?.let { Dates.format(it) } ?: "") else "Pending"
                        )
                    }
                    val f = PdfStatement.create(
                        ctx, "${chit.name}_statement", "Chit statement",
                        listOf(
                            "Chit: ${chit.name}   (${n} months x ${money(chit.monthlyAmount)} = ${money(value)})",
                            "Organiser: ${chit.organiserName}" + if (chit.place.isNotBlank()) ", ${chit.place}" else "",
                            "Date: ${Dates.format(Dates.todayUtc())}"
                        ),
                        rows,
                        listOf("Paid so far: ${money(paidTotal)}", "Pending: ${money(dueTotal - paidTotal)}") +
                            (resultLines(chit, bids) ?: emptyList())
                    )
                    PdfStatement.share(ctx, f, "${chit.name} statement")
                }
            ) { Text(if (completed) "Final PDF statement" else "Share PDF statement") }
        }

        item {
            Text("Every month", style = MaterialTheme.typography.titleMedium)
            Text("Tap a month to enter the bid or mark it paid.", style = MaterialTheme.typography.bodySmall)
        }
        items((1..n).toList()) { m ->
            val bid = bids[m]
            val paid = m in paidMonths
            val status = statusOf(chit, m, bid != null, paid)
            Card(Modifier.fillMaxWidth().clickable { editMonth = m }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val d = Dates.monthDate(chit, m)?.let { Dates.format(it) }
                        Text(
                            "Month $m" + (d?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        StatusPill(status)
                        Spacer(Modifier.height(4.dp))
                        if (bid != null) {
                            Text("Bid ${money(bid)}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                (if (paid) "Paid " else "To pay ") +
                                    money(ChitMath.installment(chit.monthlyAmount, n, bid)),
                                fontWeight = FontWeight.Bold
                            )
                            paidOn[m]?.let {
                                if (paid) Text("Paid on ${Dates.format(it)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Profit ${money(bid / n)}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Bid not entered", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (bid != null) {
                        GainChip(ChitMath.gainPercent(chit.monthlyAmount, n, bid), maxGain)
                    }
                }
            }
        }
    }

    editMonth?.let { m ->
        MonthDialog(
            month = m,
            existing = bids[m],
            paid = m in paidMonths,
            paidMillis = paidOn[m],
            onDismiss = { editMonth = null },
            onSave = { b, paid, paidDate ->
                if (b == null) {
                    vm.deleteAuction(chit.id, m)
                } else {
                    val existingId = auctions.find { it.monthNo == m }?.id ?: 0L
                    vm.saveAuction(Auction(id = existingId, chitId = chit.id, monthNo = m, bid = b.coerceIn(0.0, value)))
                }
                vm.setPaid(chit.id, 0L, m, paid, paidDate)
                editMonth = null
            }
        )
    }
    if (editWin) {
        WinDialog(
            current = chit.myWonMonth,
            max = n,
            onDismiss = { editWin = false },
            onSave = { vm.updateChit(chit.copy(myWonMonth = it)); editWin = false }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

/** Yearly interest. Only shown after the member presses the button. */
@Composable
private fun InterestSection(chit: Chit, bids: Map<Int, Double>, onHide: () -> Unit) {
    val n = chit.members
    val assumedLast = chit.myWonMonth == null
    val result = ChitMath.analyse(chit, bids, chit.myWonMonth ?: n)
    val curve = (1..n).map { ChitMath.analyse(chit, bids, it).signedAnnualPercent }
    val missing = (1..n).any { it !in bids }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Yearly interest", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            if (assumedLast) {
                Text(
                    "Assuming you take the money in the last month (month $n). Tap Change next to Chit taken if you win earlier.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
            }
            val s = result.signedAnnualPercent
            if (s == null) {
                Text("No single yearly rate fits this pattern.", fontWeight = FontWeight.SemiBold)
                Text("Over the chit you pay ${money(result.totalPaid)} and receive ${money(result.prize)}.")
            } else {
                Text(
                    if (s >= 0) "You earn about ${pct(abs(s))} a year" else "You pay about ${pct(abs(s))} a year",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (s >= 0) Green else Red
                )
                val monthly = (Math.pow(1 + abs(s) / 100.0, 1.0 / 12.0) - 1) * 100
                Text(
                    "That is about ${pct(monthly)} every month (the monthly rate, compounded over 12 months).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                Text("Total you pay over $n months: ${money(result.totalPaid)}")
                Text("Amount you receive: ${money(result.prize)}")
                Text(
                    if (result.net >= 0) "Interest earned: ${money(result.net)}" else "Interest paid: ${money(-result.net)}",
                    fontWeight = FontWeight.Medium
                )
            }
            if (missing) {
                Spacer(Modifier.height(6.dp))
                Text("Estimate: months without a bid reuse the last known bid.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Text("Yearly interest by winning month", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            RateChart(curve, chit.myWonMonth)
            Text(
                "Green: you earn interest. Red: you pay interest. No bar: no single rate fits that month.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Text("How this is worked out", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. Every month you pay the monthly amount minus your share of that month's bid.\n" +
                    "2. In the month you take the chit you receive: total value − your bid − commission.\n" +
                    "3. We find the one monthly rate at which everything you pay, valued at that rate, equals what you receive. Banks call this the IRR.\n" +
                    "4. Yearly rate = (1 + monthly rate)^12 − 1.\n" +
                    "A pay rate means taking the money early costs you; an earn rate means you gain by waiting.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onHide) { Text("Hide yearly interest") }
        }
    }
}

@Composable
private fun MonthDialog(
    month: Int,
    existing: Double?,
    paid: Boolean,
    paidMillis: Long?,
    onDismiss: () -> Unit,
    onSave: (Double?, Boolean, Long?) -> Unit
) {
    var paidOn by remember { mutableStateOf(paidMillis ?: Dates.todayUtc()) }
    var pick by remember { mutableStateOf(false) }
    var bid by remember { mutableStateOf(existing?.toLong()?.toString() ?: "") }
    var isPaid by remember { mutableStateOf(paid) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Month $month") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Bid amount (₹, 0 if no bid)", bid) { bid = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                    Text("I have paid this month")
                }
                if (isPaid) {
                    OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Paid on: ${Dates.format(paidOn)}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bid.isBlank() || bid.toDoubleOrNull() != null,
                onClick = { onSave(bid.toDoubleOrNull(), isPaid, if (isPaid) paidOn else null) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (pick) DatePickDialog(paidOn, { pick = false }) { paidOn = it; pick = false }
}

@Composable
private fun WinDialog(current: Int?, max: Int, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var v by remember { mutableStateOf(current?.toString() ?: "") }
    val parsed = v.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chit taken in month") },
        text = { NumField("Month (1 to $max, blank if not taken)", v, decimal = false) { v = it } },
        confirmButton = {
            TextButton(
                enabled = v.isBlank() || (parsed != null && parsed in 1..max),
                onClick = { onSave(parsed) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Profit bars above the axis. A month with no bar has a 0 bid or no bid yet. */
@Composable
private fun ProfitChart(profits: List<Double?>, maxProfit: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                val n = profits.size
                val w = size.width / n
                val base = size.height - 2f
                drawLine(Color.Gray, Offset(0f, base), Offset(size.width, base), strokeWidth = 2f)
                profits.forEachIndexed { i, p ->
                    if (p != null && p > 0 && maxProfit > 0) {
                        val t = (p / maxProfit).toFloat()
                        val h = t * (base - 8f)
                        drawRect(
                            lerp(Color(0xFFA5D6A7), Color(0xFF14532D), t),
                            Offset(i * w + w * 0.15f, base - h),
                            Size(w * 0.7f, h)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                profits.indices.forEach { i ->
                    Text(
                        "${i + 1}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 9.sp
                    )
                }
            }
            Text("Month number. Taller, darker bar = bigger profit.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RateChart(values: List<Double?>, highlight: Int?) {
    val maxAbs = values.filterNotNull().maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
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
