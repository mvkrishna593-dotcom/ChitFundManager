package com.chitfund.app.ui

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chitfund.app.*
import kotlin.math.abs

private val FGreen = Color(0xFF2E7D32)
private val FRed = Color(0xFFC62828)

fun isFriendship(c: Chit) = c.kind == "FRIENDSHIP"

/** Month each member takes the chit, from the schedule the manager recorded. */
fun takeMonths(auctions: List<Auction>): Map<Long, Int> =
    auctions.mapNotNull { a -> a.winnerMemberId?.let { it to a.monthNo } }.toMap()

// ------------------------------------------------------------------ form

/** Add or edit a Friendship chit, for a manager or a member. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendshipChitForm(vm: ChitViewModel, role: String, onDone: () -> Unit, existing: Chit? = null) {
    val isMember = (existing?.role ?: role) == "PARTICIPANT"
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var organiser by remember { mutableStateOf(existing?.organiserName ?: "") }
    var mobile by remember { mutableStateOf(existing?.organiserMobile ?: "") }
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var monthly by remember { mutableStateOf(existing?.let { numText(it.monthlyAmount) } ?: "") }
    var months by remember { mutableStateOf(existing?.members?.toString() ?: "") }
    var rate by remember { mutableStateOf(existing?.let { numText(it.interestRate) } ?: "1") }
    var comm by remember { mutableStateOf(existing?.let { numText(it.commissionAmount) } ?: "0") }
    var start by remember { mutableStateOf(existing?.startMillis) }
    var pick by remember { mutableStateOf(false) }

    val amt = monthly.toDoubleOrNull()
    val m = months.toIntOrNull()
    val r = rate.toDoubleOrNull()
    val cm = comm.toDoubleOrNull()
    val capital = if (amt != null && m != null) amt * m else null
    val interest = if (capital != null && r != null) capital * r / 100.0 else null
    val valid = name.isNotBlank() && (!isMember || organiser.isNotBlank()) &&
        amt != null && amt > 0 && m != null && m >= 12 &&
        r != null && r >= 0 && cm != null && cm >= 0 && (capital == null || cm < capital) &&
        start != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (existing != null) "Edit" else "New") + " friendship chit",
                        fontWeight = FontWeight.Bold
                    )
                },
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
            if (isMember) {
                Field("Organiser name", organiser) { organiser = it }
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { v -> if (v.length <= 15 && v.all { it.isDigit() || it == '+' }) mobile = v },
                    label = { Text("Organiser mobile number") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Field("Place", place) { place = it }
            }
            NumField("Amount per month (₹)", monthly) { monthly = it }
            NumField("Number of months = number of members (minimum 12)", months, decimal = false) { months = it }
            if (m != null && m < 12) {
                Text("A friendship chit needs at least 12 months", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (capital != null) {
                Text("Chit capital: ${money(capital)}  (${m} × ${money(amt!!)})", fontWeight = FontWeight.SemiBold)
            }
            NumField("Interest: rupees per ₹100 per month (1, 2, 3 ...)", rate) { rate = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1", "2", "3").forEach { v ->
                    OutlinedButton(onClick = { rate = v }) { Text("₹$v") }
                }
            }
            if (interest != null && r != null) {
                Text(
                    "Interest each month: ${money(interest)}  (${numText(r)}% of capital a month = ${numText(r * 12)}% a year)",
                    fontWeight = FontWeight.SemiBold
                )
            }
            NumField("Commission per month (₹), 0 if none", comm) { comm = it }
            if (capital != null && cm != null && capital > 0) {
                Text("Commission = ${pct(cm / capital * 100)} of the capital each month",
                    style = MaterialTheme.typography.bodySmall)
            }
            DateButton("Starting date", start) { pick = true }
            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val made = Chit(
                        name = name.trim(), role = if (isMember) "PARTICIPANT" else "ORGANISER",
                        members = m!!, monthlyAmount = amt!!, commissionPercent = 0.0,
                        organiserName = organiser.trim(), organiserMobile = mobile, place = place.trim(),
                        startMillis = start, endMillis = start?.let { Dates.addMonths(it, m - 1) },
                        kind = "FRIENDSHIP", interestRate = r!!, commissionAmount = cm!!
                    )
                    if (existing != null) {
                        vm.updateChit(made.copy(id = existing.id, myWonMonth = existing.myWonMonth))
                    } else {
                        vm.addChit(made)
                    }
                    onDone()
                }
            ) { Text(if (existing != null) "Save changes" else "Save chit") }
            if (!isMember) Text("Next, open the chit, add your members and set who takes the chit each month.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (pick) DatePickDialog(start, { pick = false }) { start = it; pick = false }
}

// ------------------------------------------------------------------ manager: schedule

/** Manager records who takes the chit in each month. No bids. */
@Composable
fun FriendScheduleTab(vm: ChitViewModel, chit: Chit, members: List<Member>, auctions: List<Auction>) {
    val byMonth = auctions.associateBy { it.monthNo }
    var editing by remember { mutableStateOf<Int?>(null) }
    val interest = FriendMath.interest(chit)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Commission earned so far: ${money(chit.commissionAmount * auctions.size)}",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "Interest ${money(interest)} a month from each member who has taken the chit · " +
                    "commission ${money(chit.commissionAmount)} a month, deducted from that month's pot.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items((1..chit.members).toList()) { mo ->
            val a = byMonth[mo]
            Card(Modifier.fillMaxWidth().clickable { editing = mo }) {
                Column(Modifier.padding(12.dp)) {
                    val d = Dates.monthDate(chit, mo)?.let { " · " + Dates.format(it) } ?: ""
                    Text("Month $mo$d", style = MaterialTheme.typography.titleSmall)
                    Text("Pot ${money(FriendMath.pot(chit, mo))} · takes home ${money(FriendMath.payout(chit, mo))}")
                    val who = members.find { it.id == a?.winnerMemberId }?.name
                    Text(
                        if (who != null) "Taker: $who" else "Taker not set. Tap to choose.",
                        fontWeight = if (who != null) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }

    editing?.let { mo ->
        val taken = auctions.filter { it.monthNo != mo }.mapNotNull { it.winnerMemberId }.toSet()
        val existing = byMonth[mo]
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Month $mo: who takes the chit?") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    val free = members.filter { it.id !in taken }
                    if (free.isEmpty()) Text("Add members first, or every member already has a month.")
                    free.forEach { mem ->
                        val chosen = mem.id == existing?.winnerMemberId
                        Button(
                            onClick = {
                                vm.saveAuction(
                                    Auction(id = existing?.id ?: 0, chitId = chit.id, monthNo = mo, bid = 0.0,
                                        winnerMemberId = mem.id)
                                )
                                editing = null
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text(
                                mem.name + if (chosen) "  ✓" else "",
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (existing != null) TextButton(onClick = { vm.deleteAuction(chit.id, mo); editing = null }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

// ------------------------------------------------------------------ manager: messages

@Composable
fun FriendMessagesTab(chit: Chit, members: List<Member>, auctions: List<Auction>, payments: List<Payment>) {
    val ctx = LocalContext.current
    var month by rememberSaveable { mutableIntStateOf(Dates.running(chit, auctions.map { it.monthNo })) }
    var unpaidOnly by rememberSaveable { mutableStateOf(false) }
    val prefs = remember { ctx.getSharedPreferences("sent_marks", Context.MODE_PRIVATE) }
    val markKey = "${chit.id}_${month}_${if (unpaidOnly) "rem" else "first"}"
    var marks by remember { mutableIntStateOf(0) }
    val sent: Set<Long> = remember(markKey, marks) {
        prefs.getStringSet(markKey, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    }
    val takes = takeMonths(auctions)
    val paidIds = payments.filter { it.monthNo == month && it.paid }.map { it.memberId }.toSet()
    val shown = if (unpaidOnly) members.filter { it.id !in paidIds } else members
    val monthText = Dates.monthDate(chit, month)?.let { Dates.monthYear(it) } ?: "month $month"
    val interest = FriendMath.interest(chit)

    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(enabled = month > 1, onClick = { month-- }) { Text("◀ Prev") }
            Text("Month $month", style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = month < chit.members, onClick = { month++ }) { Text("Next ▶") }
        }
        Text(
            "Pot ${money(FriendMath.pot(chit, month))} · members pay ${money(chit.monthlyAmount)}, " +
                "or ${money(chit.monthlyAmount + interest)} if they have already taken the chit",
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = unpaidOnly, onCheckedChange = { unpaidOnly = it })
            Spacer(Modifier.width(8.dp))
            Text("Only members who have not paid (reminder)")
        }
        Text("Tap Send. WhatsApp opens with the message ready; press send there.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { mem ->
                val due = FriendMath.due(chit, month, takes[mem.id])
                val hasInterest = due > chit.monthlyAmount
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mem.name, fontWeight = FontWeight.Medium)
                            Text(
                                (if (mem.phone.isBlank()) "No WhatsApp number" else mem.phone) +
                                    " · pays ${money(due)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (mem.id in sent) Text("Message sent ✓", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            enabled = mem.phone.isNotBlank(),
                            onClick = {
                                val detail = if (hasInterest) " (${money(chit.monthlyAmount)} + interest ${money(interest)})" else ""
                                val text = if (unpaidOnly) {
                                    "Hello ${mem.name}, a gentle reminder: your chit amount ${money(due)}$detail for ${chit.name} ($monthText) is still pending. Please pay at the earliest. Thank you."
                                } else {
                                    "Hello ${mem.name}, ${chit.name} chit, $monthText: your amount to pay this month is ${money(due)}$detail. Please pay within this week. Thank you."
                                }
                                prefs.edit()
                                    .putStringSet(markKey, (sent + mem.id).map { it.toString() }.toSet())
                                    .apply()
                                marks++
                                openWhatsApp(ctx, mem.phone, text)
                            }
                        ) { Text("Send") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ results and statements

fun friendResultLines(chit: Chit, take: Int): List<String> {
    val a = FriendMath.analyse(chit, take)
    val pctOfReceived = if (a.prize > 0) abs(a.net) / a.prize * 100 else 0.0
    val lines = mutableListOf(
        "Chit taken in month $take",
        "Amount received: ${money(a.prize)}  (pot ${money(FriendMath.pot(chit, take))} - commission ${money(chit.commissionAmount)})",
        "Total paid over ${chit.members} months: ${money(a.totalPaid)}",
        (if (a.net >= 0) "Gain: " else "Loss (interest paid): ") + "${money(abs(a.net))} = ${pct(pctOfReceived)} of the amount received"
    )
    a.signedAnnualPercent?.let {
        lines += (if (it >= 0) "Interest earned" else "Interest paid") + ": about ${pct(abs(it))} a year"
    }
    return lines
}

/** PDF for one member of a friendship chit the manager runs. */
fun friendMemberStatement(
    ctx: Context, chit: Chit, m: Member, auctions: List<Auction>, payments: List<Payment>
): java.io.File {
    val take = takeMonths(auctions)[m.id]
    val mine = payments.filter { it.memberId == m.id && it.paid }.associateBy { it.monthNo }
    var paidTotal = 0.0
    var dueTotal = 0.0
    val rows = (1..chit.members).map { mo ->
        val due = FriendMath.due(chit, mo, take)
        val p = mine[mo]
        dueTotal += due
        if (p != null) paidTotal += due
        StatementRow(
            "Month $mo",
            Dates.monthDate(chit, mo)?.let { Dates.format(it) } ?: "-",
            if (mo == take) money(FriendMath.payout(chit, mo)) else "-",
            money(due),
            if (p != null) "Paid " + (p.paidMillis?.let { Dates.format(it) } ?: "") else "Pending"
        )
    }
    val footer = mutableListOf("Paid so far: ${money(paidTotal)}", "Pending: ${money(dueTotal - paidTotal)}")
    if (take != null) footer += friendResultLines(chit, take)
    return PdfStatement.create(
        ctx, "${chit.name}_${m.name}", "Friendship chit statement",
        listOf(
            "Chit: ${chit.name}   (${chit.members} months x ${money(chit.monthlyAmount)} = ${money(FriendMath.capital(chit))})",
            "Interest ${money(FriendMath.interest(chit))} a month after taking the chit",
            "Member: ${m.name}" + if (m.place.isNotBlank()) ", ${m.place}" else "",
            "Date: ${Dates.format(Dates.todayUtc())}"
        ),
        rows, footer, bidHeader = "Receive"
    )
}

// ------------------------------------------------------------------ member's own friendship chit

@Composable
fun FriendshipMemberContent(vm: ChitViewModel, chit: Chit) {
    val ctx = LocalContext.current
    val payments by vm.payments(chit.id).collectAsState(emptyList())
    val paidOn = payments.filter { it.paid }.associate { it.monthNo to it.paidMillis }
    var editMonth by remember { mutableStateOf<Int?>(null) }
    var editWin by remember { mutableStateOf(false) }
    var showRate by remember { mutableStateOf(false) }

    val n = chit.members
    val take = chit.myWonMonth
    val interest = FriendMath.interest(chit)
    val completed = (1..n).all { it in paidOn }
    val remaining = if (chit.startMillis == null) n else n - Dates.elapsed(chit)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Friendship chit", style = MaterialTheme.typography.titleSmall)
                    InfoLine("Organiser", chit.organiserName.ifBlank { "—" })
                    InfoLine("Mobile", chit.organiserMobile.ifBlank { "—" })
                    InfoLine("Place", chit.place.ifBlank { "—" })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    InfoLine("Chit capital", money(FriendMath.capital(chit)))
                    InfoLine("Amount per month", money(chit.monthlyAmount))
                    InfoLine("Interest", "${money(interest)} / month (₹${numText(chit.interestRate)} per ₹100)")
                    InfoLine("Commission", money(chit.commissionAmount) + " / month")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chit taken", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (take != null) "Yes (month $take)" else "No", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { editWin = true }) { Text("Change") }
                        }
                    }
                    InfoLine("Started", chit.startMillis?.let { Dates.format(it) } ?: "—")
                    InfoLine("Remaining months", "$remaining of $n")
                }
            }
        }

        if (take != null) {
            item {
                val a = FriendMath.analyse(chit, take)
                val pctOfReceived = if (a.prize > 0) abs(a.net) / a.prize * 100 else 0.0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (completed) "Chit completed ✓" else "Your result", style = MaterialTheme.typography.titleMedium)
                        InfoLine("Chit taken in month", "$take")
                        InfoLine("Amount received", money(a.prize))
                        Text(
                            "Pot ${money(FriendMath.pot(chit, take))} − commission ${money(chit.commissionAmount)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        InfoLine("Total paid (all $n months)", money(a.totalPaid))
                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                        Text(
                            if (a.net >= 0) "Gain ${money(a.net)} (${pct(pctOfReceived)})"
                            else "Loss ${money(-a.net)} (${pct(pctOfReceived)})",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (a.net >= 0) FGreen else FRed
                        )
                        Text("Percent is of the amount you received.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                if (!showRate) {
                    OutlinedButton(onClick = { showRate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("View yearly interest")
                    }
                } else {
                    val a = FriendMath.analyse(chit, take)
                    val curve = (1..n).map { FriendMath.analyse(chit, it).signedAnnualPercent }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Yearly interest", style = MaterialTheme.typography.titleMedium)
                            val sgn = a.signedAnnualPercent
                            if (sgn == null) {
                                Text("No single yearly rate fits this pattern.", fontWeight = FontWeight.SemiBold)
                            } else {
                                Text(
                                    if (sgn >= 0) "You earn about ${pct(abs(sgn))} a year" else "You pay about ${pct(abs(sgn))} a year",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (sgn >= 0) FGreen else FRed
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Yearly interest by month you take the chit", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            RateChart(curve, take)
                            Text("Green: you earn interest. Red: you pay interest. No bar: no single rate fits that month.",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Each month you pay the monthly amount, plus the interest once you have taken the chit. " +
                                    "In the month you take it you receive the pot minus commission. " +
                                    "The yearly rate is the monthly rate that balances what you pay with what you receive, compounded over 12 months.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { showRate = false }) { Text("Hide yearly interest") }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    var paidTotal = 0.0
                    var dueTotal = 0.0
                    val rows = (1..n).map { mo ->
                        val due = FriendMath.due(chit, mo, take)
                        dueTotal += due
                        if (mo in paidOn) paidTotal += due
                        StatementRow(
                            "Month $mo",
                            Dates.monthDate(chit, mo)?.let { Dates.format(it) } ?: "-",
                            if (mo == take) money(FriendMath.payout(chit, mo)) else "-",
                            money(due),
                            if (mo in paidOn) "Paid " + (paidOn[mo]?.let { Dates.format(it) } ?: "") else "Pending"
                        )
                    }
                    val footer = mutableListOf("Paid so far: ${money(paidTotal)}", "Pending: ${money(dueTotal - paidTotal)}")
                    if (take != null) footer += friendResultLines(chit, take)
                    val f = PdfStatement.create(
                        ctx, "${chit.name}_statement", "Friendship chit statement",
                        listOf(
                            "Chit: ${chit.name}   ($n months x ${money(chit.monthlyAmount)} = ${money(FriendMath.capital(chit))})",
                            "Interest ${money(interest)} a month after taking the chit",
                            "Organiser: ${chit.organiserName}" + if (chit.place.isNotBlank()) ", ${chit.place}" else "",
                            "Date: ${Dates.format(Dates.todayUtc())}"
                        ),
                        rows, footer, bidHeader = "Receive"
                    )
                    PdfStatement.share(ctx, f, "${chit.name} statement")
                }
            ) { Text(if (completed) "Final PDF statement" else "Share PDF statement") }
        }

        item {
            Text("Every month", style = MaterialTheme.typography.titleMedium)
            Text("Tap a month to mark it paid.", style = MaterialTheme.typography.bodySmall)
        }
        items((1..n).toList()) { mo ->
            val paid = mo in paidOn
            val due = FriendMath.due(chit, mo, take)
            val status = statusOf(chit, mo, true, paid)
            Card(Modifier.fillMaxWidth().clickable { editMonth = mo }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val d = Dates.monthDate(chit, mo)?.let { " · " + Dates.format(it) } ?: ""
                    Text("Month $mo$d", style = MaterialTheme.typography.titleSmall)
                    StatusPill(status)
                    Text((if (paid) "Paid " else "To pay ") + money(due), fontWeight = FontWeight.Bold)
                    if (due > chit.monthlyAmount) {
                        Text("${money(chit.monthlyAmount)} + interest ${money(interest)}", style = MaterialTheme.typography.bodySmall)
                    }
                    paidOn[mo]?.let {
                        if (paid) Text("Paid on ${Dates.format(it)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (mo == take) {
                        Text("You receive ${money(FriendMath.payout(chit, mo))} this month",
                            fontWeight = FontWeight.Bold, color = FGreen)
                    }
                }
            }
        }
    }

    editMonth?.let { mo ->
        FriendPayDialog(
            month = mo, paid = mo in paidOn, paidMillis = paidOn[mo],
            onDismiss = { editMonth = null },
            onSave = { paid, date -> vm.setPaid(chit.id, 0L, mo, paid, date); editMonth = null }
        )
    }
    if (editWin) {
        WinDialog(
            current = take, max = n, onDismiss = { editWin = false },
            onSave = { vm.updateChit(chit.copy(myWonMonth = it)); editWin = false }
        )
    }
}

@Composable
private fun FriendPayDialog(
    month: Int,
    paid: Boolean,
    paidMillis: Long?,
    onDismiss: () -> Unit,
    onSave: (Boolean, Long?) -> Unit
) {
    var isPaid by remember { mutableStateOf(paid) }
    var on by remember { mutableStateOf(paidMillis ?: Dates.todayUtc()) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Month $month") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                    Text("I have paid this month")
                }
                if (isPaid) {
                    OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Paid on: ${Dates.format(on)}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(isPaid, if (isPaid) on else null) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (pick) DatePickDialog(on, { pick = false }) { on = it; pick = false }
}
