package com.chitfund.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chitfund.app.*

@Composable
fun OrganiserContent(vm: ChitViewModel, chit: Chit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val members by vm.members(chit.id).collectAsState(emptyList())
    val auctions by vm.auctions(chit.id).collectAsState(emptyList())
    val payments by vm.payments(chit.id).collectAsState(emptyList())

    Column {
        PillTabs(listOf("Members", "Auctions", "Payments"), tab) { tab = it }
        when (tab) {
            0 -> MembersTab(vm, chit, members)
            1 -> AuctionsTab(vm, chit, members, auctions)
            else -> PaymentsTab(vm, chit, members, auctions, payments)
        }
    }
}

@Composable
private fun MembersTab(vm: ChitViewModel, chit: Chit, members: List<Member>) {
    var show by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("${members.size} of ${chit.members} members", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { show = true }, enabled = members.size < chit.members) { Text("Add member") }
            }
        }
        items(members, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.Medium)
                        if (m.phone.isNotBlank()) Text(m.phone, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.removeMember(m) }) { Text("Remove") }
                }
            }
        }
    }
    if (show) {
        MemberDialog(onDismiss = { show = false }) { n, p -> vm.addMember(chit.id, n, p); show = false }
    }
}

@Composable
private fun MemberDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), phone.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AuctionsTab(vm: ChitViewModel, chit: Chit, members: List<Member>, auctions: List<Auction>) {
    val byMonth = auctions.associateBy { it.monthNo }
    var editing by remember { mutableStateOf<Int?>(null) }
    val value = ChitMath.chitValue(chit)
    val commissionEach = ChitMath.commission(value, chit.commissionPercent)
    val maxGain = auctions.maxOfOrNull { ChitMath.gainPercent(chit.monthlyAmount, chit.members, it.bid) } ?: 0.0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Commission earned so far: ${money(commissionEach * auctions.size)}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${pct(chit.commissionPercent)} of ${money(value)} = ${money(commissionEach)} per month, deducted from the winner.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items((1..chit.members).toList()) { m ->
            val a = byMonth[m]
            Card(Modifier.fillMaxWidth().clickable { editing = m }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Month $m", style = MaterialTheme.typography.titleSmall)
                        if (a == null) {
                            Text("Not held yet — tap to enter")
                        } else {
                            Text("Bid ${money(a.bid)}")
                            Text(
                                "Each member pays ${money(ChitMath.installment(chit.monthlyAmount, chit.members, a.bid))}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Winner: ${members.find { it.id == a.winnerMemberId }?.name ?: "—"} · takes home ${money(ChitMath.prize(value, a.bid, chit.commissionPercent))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (a != null) {
                        GainChip(ChitMath.gainPercent(chit.monthlyAmount, chit.members, a.bid), maxGain)
                    }
                }
            }
        }
    }

    editing?.let { m ->
        val wonElsewhere = auctions.filter { it.monthNo != m }.mapNotNull { it.winnerMemberId }.toSet()
        AuctionDialog(
            month = m,
            existing = byMonth[m],
            eligible = members.filter { it.id !in wonElsewhere },
            allMembers = members,
            onDismiss = { editing = null },
            onClear = { vm.deleteAuction(chit.id, m); editing = null },
            onSave = { bid, winner ->
                vm.saveAuction(
                    Auction(
                        id = byMonth[m]?.id ?: 0,
                        chitId = chit.id, monthNo = m,
                        bid = bid.coerceIn(0.0, value), winnerMemberId = winner
                    )
                )
                editing = null
            }
        )
    }
}

@Composable
private fun AuctionDialog(
    month: Int,
    existing: Auction?,
    eligible: List<Member>,
    allMembers: List<Member>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSave: (Double, Long?) -> Unit
) {
    var bid by remember { mutableStateOf(existing?.bid?.toLong()?.toString() ?: "") }
    var winner by remember { mutableStateOf(existing?.winnerMemberId) }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Month $month auction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField("Bid amount (discount, ₹)", bid) { bid = it }
                Box {
                    OutlinedButton(onClick = { menu = true }) {
                        Text(allMembers.find { it.id == winner }?.name ?: "Select winner")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        eligible.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = { winner = m.id; menu = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = bid.toDoubleOrNull() != null, onClick = { onSave(bid.toDouble(), winner) }) { Text("Save") }
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
private fun PaymentsTab(
    vm: ChitViewModel,
    chit: Chit,
    members: List<Member>,
    auctions: List<Auction>,
    payments: List<Payment>
) {
    var month by rememberSaveable { mutableIntStateOf(1) }
    val auction = auctions.find { it.monthNo == month }
    val due = ChitMath.installment(chit.monthlyAmount, chit.members, auction?.bid ?: 0.0)
    val paidSet = payments.filter { it.monthNo == month && it.paid }.map { it.memberId }.toSet()
    val paidCount = members.count { it.id in paidSet }

    Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(enabled = month > 1, onClick = { month-- }) { Text("◀ Prev") }
            Text("Month $month", style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = month < chit.members, onClick = { month++ }) { Text("Next ▶") }
        }
        if (auction == null) {
            Text(
                "This month's auction isn't entered yet, so the full ${money(chit.monthlyAmount)} per member is shown.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Each member pays ${money(due)}", fontWeight = FontWeight.Medium)
        Text("Collected ${money(paidCount * due)} · Pending ${money((members.size - paidCount) * due)}")
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(members, key = { it.id }) { m ->
                val paid = m.id in paidSet
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setPaid(chit.id, m.id, month, !paid) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = paid, onCheckedChange = { vm.setPaid(chit.id, m.id, month, it) })
                    Text(m.name)
                }
            }
        }
    }
}
