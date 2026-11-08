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
fun MemberHome(vm: ChitViewModel, chits: List<Chit>, onOpen: (Long) -> Unit) {
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
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { onOpen(c.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
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
            }
        }
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
    val dateText = Dates.monthDate(c, month)?.let { Dates.monthYear(it) } ?: "Month $month"

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2E8B5E), Color(0xFF52B788))))
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Text("$dateText  ·  ${c.name}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Month $month/${c.members}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text("Bid amount", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Text(
                bid?.let { money(it) } ?: "Not entered",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (paid) "Amount paid" else "Amount to pay",
                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp
            )
            Text(
                pay?.let { money(it) } ?: "—",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.align(Alignment.TopEnd)) { StatusPill(status) }
    }
}

@Composable
fun ParticipantContent(vm: ChitViewModel, chit: Chit) {
    val auctions by vm.auctions(chit.id).collectAsState(emptyList())
    val payments by vm.payments(chit.id).collectAsState(emptyList())
    val bids = auctions.associate { it.monthNo to it.bid }
    val paidMonths = payments.filter { it.paid }.map { it.monthNo }.toSet()
    var editMonth by remember { mutableStateOf<Int?>(null) }
    var editWin by remember { mutableStateOf(false) }
    var showInterest by remember { mutableStateOf(false) }

    val n = chit.members
    val value = ChitMath.chitValue(chit)
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
            onDismiss = { editMonth = null },
            onSave = { b, paid ->
                if (b == null) {
                    vm.deleteAuction(chit.id, m)
                } else {
                    val existingId = auctions.find { it.monthNo == m }?.id ?: 0L
                    vm.saveAuction(Auction(id = existingId, chitId = chit.id, monthNo = m, bid = b.coerceIn(0.0, value)))
                }
                vm.setPaid(chit.id, 0L, m, paid)
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
            Text("Green: you earn interest. Red: you pay interest.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onHide) { Text("Hide yearly interest") }
        }
    }
}

@Composable
private fun MonthDialog(
    month: Int,
    existing: Double?,
    paid: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double?, Boolean) -> Unit
) {
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = bid.isBlank() || bid.toDoubleOrNull() != null,
                onClick = { onSave(bid.toDoubleOrNull(), isPaid) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
