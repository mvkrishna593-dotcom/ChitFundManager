package com.chitfund.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitfund.app.Chit
import com.chitfund.app.ChitViewModel
import com.chitfund.app.Profile
import com.chitfund.app.ProfileViewModel
import com.chitfund.app.Updater
import kotlinx.coroutines.launch

private val Green = Color(0xFF0F5132)

/**
 * Home with the side menu. Two tabs: Manager (chits I run) and Member (chits I joined).
 * It opens on Manager if the person runs at least one chit, otherwise on Member.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: ChitViewModel,
    profileVm: ProfileViewModel,
    onOpen: (Long) -> Unit,
    onNew: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val profile by profileVm.profile.collectAsState()
    val managerChits by remember { vm.chits("ORGANISER") }.collectAsState(initial = null)

    // -1 = not decided yet, 0 = Manager, 1 = Member
    var tab by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(managerChits) {
        val m = managerChits
        if (tab == -1 && m != null) tab = if (m.isNotEmpty()) 0 else 1
    }

    val role = if (tab == 0) "ORGANISER" else "PARTICIPANT"
    val chits by remember(role) { vm.chits(role) }.collectAsState(emptyList())

    var manualCheck by remember { mutableIntStateOf(0) }
    UpdateHost(manualCheck)

    val versionLabel = remember {
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "Version ${info.versionName} (build ${Updater.currentBuild(ctx)})"
        } catch (e: Exception) {
            "Version"
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = Color(0xFFE3F1E8),
        selectedTextColor = Green,
        selectedIconColor = Green
    )
    val itemPad = NavigationDrawerItemDefaults.ItemPadding

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                DrawerHeader(profile) {
                    closeDrawer()
                    onNavigate("profile")
                }
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = true,
                    icon = { Icon(Icons.Filled.Home, null) },
                    onClick = { closeDrawer() },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                NavigationDrawerItem(
                    label = { Text("Chit Manager") },
                    selected = false,
                    icon = { Icon(Icons.Filled.AccountBalance, null) },
                    onClick = { tab = 0; closeDrawer() },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                NavigationDrawerItem(
                    label = { Text("Chit Member") },
                    selected = false,
                    icon = { Icon(Icons.Filled.Group, null) },
                    onClick = { tab = 1; closeDrawer() },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 16.dp))
                NavigationDrawerItem(
                    label = { Text("Profile") },
                    selected = false,
                    icon = { Icon(Icons.Filled.Person, null) },
                    onClick = { closeDrawer(); onNavigate("profile") },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                NavigationDrawerItem(
                    label = { Text("App Settings") },
                    selected = false,
                    icon = { Icon(Icons.Filled.Settings, null) },
                    onClick = { closeDrawer(); onNavigate("settings") },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                NavigationDrawerItem(
                    label = { Text("Help") },
                    selected = false,
                    icon = { Icon(Icons.AutoMirrored.Filled.Help, null) },
                    onClick = { closeDrawer(); onNavigate("help") },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                NavigationDrawerItem(
                    label = { Text("Update") },
                    selected = false,
                    icon = { Icon(Icons.Filled.SystemUpdate, null) },
                    onClick = { closeDrawer(); manualCheck++ },
                    colors = itemColors,
                    modifier = Modifier.padding(itemPad)
                )
                Spacer(Modifier.weight(1f))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(versionLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Home, null, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("CHIT FUND MANAGER", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "Menu")
                        }
                    },
                    colors = headerColors()
                )
            },
            floatingActionButton = {
                if (tab >= 0 && chits.isNotEmpty()) {
                    ExtendedFloatingActionButton(onClick = { onNew(role) }) {
                        Text(if (tab == 0) "+ New chit" else "+ Add chit")
                    }
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad)) {
                if (tab >= 0) {
                    PillTabs(listOf("Manager", "Member"), tab) { tab = it }
                    if (chits.isEmpty()) {
                        EmptyState(isManager = tab == 0) { onNew(role) }
                    } else if (tab == 1) {
                        MemberHome(vm, chits, onOpen)
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(chits, key = { it.id }) { c -> ChitCard(c) { onOpen(c.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(profile: Profile, onEdit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Color(0xFF0F5132), Color(0xFF198754))))
            .padding(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.photoPath, 76.dp)
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Filled.Edit, "Edit profile", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            profile.name.ifBlank { "Your name" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            profile.mobile.ifBlank { "Add your mobile number" },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onEdit)
        )
    }
}

@Composable
private fun ChitCard(c: Chit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleMedium)
            Text("${c.members} members · ${money(c.monthlyAmount)}/month · ${pct(c.commissionPercent)} commission")
            Text("Chit value ${money(c.members * c.monthlyAmount)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(isManager: Boolean, onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isManager) Icons.Filled.AccountBalance else Icons.Filled.Group,
                null,
                tint = Color(0xFF7F9A8C),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (isManager) "You are not running a chit yet" else "You have not added a chit yet",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isManager) "Create a chit to add members, enter each month's auction and send payment messages on WhatsApp."
                else "Add a chit you have joined to see what you pay each month and your profit.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCreate) { Text(if (isManager) "Create my first chit" else "Add my first chit") }
        }
    }
}
