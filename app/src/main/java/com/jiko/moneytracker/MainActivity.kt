package com.jiko.moneytracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.data.Txn
import com.jiko.moneytracker.ui.screens.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.SaldoTheme
import com.jiko.moneytracker.ui.theme.saldoColors

class MainActivity : ComponentActivity() {
    private val vm: MoneyViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode by vm.darkMode.collectAsState()
            var unlocked by remember { mutableStateOf(!vm.prefs.lockEnabled) }
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                vm.toast.collect { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            SaldoTheme(darkMode = darkMode) {
                if (!unlocked) {
                    LockScreen(vm = vm, onUnlocked = { unlocked = true })
                } else {
                    AppScaffold(vm = vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(vm: MoneyViewModel) {
    // Manual bottom-tab navigation (no nav-compose dependency).
    val backStack = remember { mutableStateListOf("home") }
    val route = backStack.last()

    fun navigate(r: String) {
        if (backStack.last() != r) backStack.add(r)
    }
    fun back() {
        if (backStack.size > 1) backStack.removeLast()
    }
    BackHandler(enabled = backStack.size > 1) { back() }

    var showAdd by remember { mutableStateOf(false) }
    var showScan by remember { mutableStateOf(false) }
    var editingTxn by remember { mutableStateOf<Txn?>(null) }
    var presetCat by remember { mutableStateOf(-1L) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scanSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun openAdd(edit: Txn? = null, catId: Long = -1) {
        editingTxn = edit
        presetCat = catId
        showAdd = true
    }

    val tabs = listOf(
        Tab("home", "Beranda", Icons.Filled.Home),
        Tab("txns", "Transaksi", Icons.Filled.List),
        Tab("stats", "Statistik", Icons.Filled.PieChart),
        Tab("more", "Lainnya", Icons.Filled.GridView),
    )
    // Web TABMAP: sub-screens highlight "more".
    val activeTab = when (route) {
        "home" -> "home"
        "txns" -> "txns"
        "stats" -> "stats"
        else -> "more"
    }
    val pal = saldoColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Custom bar so the FAB can overflow; visible on ALL routes.
            Column(
                Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                HorizontalDivider(thickness = 1.dp, color = pal.line)
                Row(
                    Modifier.fillMaxWidth()
                        .padding(
                            top = 6.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.take(2).forEach { t ->
                        TabItem(t, activeTab == t.route, Modifier.weight(1f)) { navigate(t.route) }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FloatingActionButton(
                            onClick = { openAdd() },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = pal.accent,
                            contentColor = pal.accentInk,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 8.dp
                            ),
                            modifier = Modifier.size(52.dp).offset(y = (-22).dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Tambah")
                        }
                    }
                    tabs.drop(2).forEach { t ->
                        TabItem(t, activeTab == t.route, Modifier.weight(1f)) { navigate(t.route) }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (route) {
                "home" -> HomeScreen(
                    vm = vm,
                    onAdd = { openAdd() },
                    onSeeAllTxns = { navigate("txns") },
                    onTxnClick = { openAdd(edit = it) },
                    onDrillCategory = { vm.drillToCategory(it); navigate("txns") },
                    onGoals = { navigate("goals") },
                    onAccounts = { navigate("accounts") },
                    onBudgets = { navigate("budgets") },
                    onStats = { navigate("stats") },
                    onScan = { showScan = true },
                    onForecast = { navigate("forecast") }
                )
                "txns" -> TransactionsScreen(
                    vm = vm,
                    onTxnClick = { openAdd(edit = it) },
                    onAdd = { openAdd() }
                )
                "stats" -> StatsScreen(vm = vm, onDrillCategory = {
                    vm.drillToCategory(it); navigate("txns")
                })
                "budgets" -> BudgetsScreen(vm = vm)
                "more" -> MoreScreen(
                    vm = vm,
                    onBudgets = { navigate("budgets") },
                    onCalendar = { navigate("calendar") },
                    onRecurring = { navigate("recurring") },
                    onGoals = { navigate("goals") },
                    onAccounts = { navigate("accounts") },
                    onSettings = { navigate("settings") },
                    onScanReceipt = { showScan = true },
                    onForecast = { navigate("forecast") },
                    onReports = { navigate("reports") },
                    onDebts = { navigate("debts") }
                )
                "accounts" -> AccountsScreen(
                    vm = vm,
                    onAccountTxns = { vm.drillToAccount(it); navigate("txns") },
                    onBack = { back() }
                )
                "calendar" -> CalendarScreen(
                    vm = vm, onTxnClick = { openAdd(edit = it) }, onBack = { back() }
                )
                "recurring" -> RecurringScreen(vm = vm, onBack = { back() })
                "goals" -> GoalsScreen(vm = vm, onBack = { back() })
                "settings" -> SettingsScreen(
                    vm = vm, onBack = { back() },
                    onOpenCategories = { navigate("categories") }
                )
                "categories" -> CategoriesScreen(vm = vm, onBack = { back() })
                "forecast" -> ForecastScreen(vm = vm, onBack = { back() })
                "reports" -> ReportsScreen(vm = vm, onBack = { back() })
                "debts" -> DebtsScreen(vm = vm, onBack = { back() })
            }
        }
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(38.dp, 4.5.dp)
                            .clip(RoundedCornerShape(2.25.dp))
                            .background(pal.surface3)
                    )
                }
            }
        ) {
            AddSheet(
                vm = vm,
                editing = editingTxn,
                presetCategoryId = presetCat,
                onClose = { showAdd = false },
                onScanReceipt = {
                    showAdd = false
                    showScan = true
                },
                onManageCategories = {
                    showAdd = false
                    navigate("categories")
                }
            )
        }
    }

    if (showScan) {
        ModalBottomSheet(
            onDismissRequest = { showScan = false },
            sheetState = scanSheetState,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = {
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(38.dp, 4.5.dp)
                            .clip(RoundedCornerShape(2.25.dp))
                            .background(pal.surface3)
                    )
                }
            }
        ) {
            ReceiptScanSheet(vm = vm, onClose = { showScan = false })
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun TabItem(t: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = saldoColors
    val tint = if (selected) pal.accent else pal.faint
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(t.icon, contentDescription = t.label, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(2.dp))
        Text(t.label, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
            fontSize = 10.5.sp, color = tint)
    }
}
