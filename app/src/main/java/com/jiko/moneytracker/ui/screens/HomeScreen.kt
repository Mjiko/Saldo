package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.SavingsGoal
import com.jiko.moneytracker.data.Txn
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.Forecast
import com.jiko.moneytracker.util.MoneyFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: MoneyViewModel,
    onAdd: () -> Unit,
    onSeeAllTxns: () -> Unit,
    onTxnClick: (Txn) -> Unit,
    onDrillCategory: (Long) -> Unit,
    onGoals: () -> Unit,
    onAccounts: () -> Unit,
    onBudgets: () -> Unit,
    onStats: () -> Unit,
    onScan: () -> Unit,
    onForecast: () -> Unit,
) {
    val txns by vm.txns.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val goals by vm.goals.collectAsState()
    val recurrings by vm.recurrings.collectAsState()
    val currency by vm.currency.collectAsState()
    val monthStart by vm.monthStart.collectAsState()
    val profileName by vm.profileName.collectAsState()
    val greetingName = profileName.trim().ifBlank { "Jiko" }
    val pal = saldoColors

    var hideBal by remember { mutableStateOf(vm.prefs.hideBalance) }

    val now = System.currentTimeMillis()
    val (from, to) = remember {
        val c = Calendar.getInstance()
        Dates.monthRange(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
    }
    val monthTxns = remember(txns, from, to) { txns.filter { it.timestamp in from..to } }

    val balances by remember(accounts) {
        if (accounts.isEmpty()) flowOf(emptyMap<Long, Long>())
        else combine(accounts.map { vm.balanceOf(it.id) }) { arr ->
            accounts.mapIndexed { i, a -> a.id to arr[i] }.toMap()
        }
    }.collectAsState(initial = emptyMap())
    val totalBalance = remember(balances) { balances.values.sum() }

    // Forecast teaser (web home card)
    val fc = remember(txns, recurrings, totalBalance) {
        Forecast.compute(txns, recurrings, totalBalance, now)
    }

    val income = remember(monthTxns) { monthTxns.filter { it.type == "INCOME" }.sumOf { it.amountMinor } }
    val expense = remember(monthTxns) { monthTxns.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor } }
    val net = income - expense
    val monShort = remember { Dates.monthShort(now) }

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    // Donut: top-5 expense by category, this calendar month
    var selectedSeg by remember { mutableStateOf(-1) }
    val donutData = remember(monthTxns, catMap) {
        monthTxns.filter { it.type == "EXPENSE" && it.categoryId != null }
            .groupBy { it.categoryId!! }
            .map { (catId, list) -> Triple(catId, list.sumOf { it.amountMinor }, catMap[catId]) }
            .sortedByDescending { it.second }
            .take(5)
    }
    val segments = remember(donutData) {
        donutData.mapIndexed { i, (_, v, c) ->
            DonutSegment(c?.name ?: "Lainnya", v,
                c?.let { colorOf(it.colorHex) } ?: CHART_PALETTE[i % CHART_PALETTE.size])
        }
    }

    // Budget alert: highest pct >= 75% in the current budget period
    val (bFrom, bTo) = remember(monthStart) { Dates.budgetPeriod(now, monthStart) }
    val alert = remember(budgets, txns, bFrom, bTo) {
        budgets.mapNotNull { b ->
            val spent = txns.filter {
                it.type == "EXPENSE" && it.timestamp in bFrom..bTo &&
                        (b.categoryId == null || it.categoryId == b.categoryId)
            }.sumOf { it.amountMinor }
            val pct = if (b.amountMinor > 0) spent.toDouble() / b.amountMinor else 0.0
            if (pct >= 0.75) Triple(b, spent, pct) else null
        }.maxByOrNull { it.third }
    }

    // Top goal by progress
    val topGoal = remember(goals) {
        goals.maxByOrNull { if (it.targetMinor > 0) it.savedMinor.toDouble() / it.targetMinor else 0.0 }
    }
    var fillGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val recent = remember(txns) { txns.sortedByDescending { it.timestamp }.take(5) }
    val todayText = remember {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID")).format(java.util.Date(now))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // 1. TopBar: date sub + greeting + eye toggle
        item {
            TopBar(
                title = "Halo, $greetingName",
                date = todayText,
                trailing = {
                    IconBtn(
                        icon = Icons.Filled.DocumentScanner,
                        onClick = onScan,
                        desc = "Pindai struk"
                    )
                    Spacer(Modifier.width(8.dp))
                    IconBtn(
                        icon = if (hideBal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        onClick = {
                            hideBal = !hideBal
                            vm.prefs.hideBalance = hideBal
                        },
                        desc = "Sembunyikan saldo"
                    )
                }
            )
        }
        // 2. Hero (not a card) + 3. InOut + 4. Savings strip
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Total saldo · ${accounts.size} akun",
                    fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp, color = pal.muted
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                    if (hideBal) {
                        Text(
                            "Rp •••••••",
                            fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp, letterSpacing = (-1.08).sp, color = pal.ink,
                            style = tnumStyle
                        )
                    } else {
                        Text(
                            MoneyFormat.symbol(currency),
                            fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 19.sp, color = pal.muted,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            MoneyFormat.format(totalBalance, currency)
                                .removePrefix(MoneyFormat.symbol(currency)),
                            fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp, letterSpacing = (-1.08).sp, color = pal.ink,
                            style = tnumStyle, maxLines = 1
                        )
                    }
                }
                // InOut row
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InOutCard(
                        label = "Pemasukan · $monShort",
                        value = income, currency = currency, isIncome = true,
                        hide = hideBal, modifier = Modifier.weight(1f), onClick = onStats
                    )
                    InOutCard(
                        label = "Pengeluaran · $monShort",
                        value = expense, currency = currency, isIncome = false,
                        hide = hideBal, modifier = Modifier.weight(1f), onClick = onStats
                    )
                }
                // Savings strip
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (net >= 0) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                            tint = pal.ink, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buildAnnotatedString {
                            append("Bulan ini kamu menabung ")
                            withStyle(
                                SpanStyle(fontWeight = FontWeight.Bold, color = pal.successText)
                            ) { append(MoneyFormat.formatCompact(net, currency)) }
                            append(" — pertahankan ritmenya.")
                        }, fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted)
                    } else {
                        Icon(Icons.Filled.Notifications, contentDescription = null,
                            tint = pal.warningText, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buildAnnotatedString {
                            append("Pengeluaran bulan ini melebihi pemasukan sebesar ")
                            withStyle(
                                SpanStyle(fontWeight = FontWeight.Bold, color = pal.warningText)
                            ) { append(MoneyFormat.formatCompact(-net, currency)) }
                            append(".")
                        }, fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted)
                    }
                }
            }
        }
        // 5. Accounts chips
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Akun")
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.id }) { a ->
                    Row(
                        Modifier.shadow(2.dp, RoundedCornerShape(99.dp))
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onAccounts() }
                            .padding(start = 9.dp, end = 13.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(colorOf(a.colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconFor(a.iconKey), contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(a.name, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = pal.ink)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            MoneyFormat.formatCompact(balances[a.id] ?: 0L, currency),
                            fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp, color = pal.muted,
                            style = tnumStyle
                        )
                    }
                }
            }
        }
        // 6. Budget alert banner
        if (alert != null) {
            item {
                val (b, _, pct) = alert
                val catName = b.categoryId?.let { catMap[it]?.name } ?: "Semua pengeluaran"
                SaldoCard(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
                    onClick = onBudgets
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                                .background(pal.alertIconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null,
                                tint = pal.alertIconFg, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Anggaran $catName sudah ${(pct * 100).roundToInt()}%",
                                fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = pal.ink
                            )
                            Text(
                                if (pct >= 1) "Sudah melewati batas ${MoneyFormat.format(b.amountMinor, currency)} bulan ini."
                                else "Mendekati batas ${MoneyFormat.format(b.amountMinor, currency)} — hati-hati di sisa bulan ini.",
                                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                            )
                        }
                    }
                }
            }
        }
        // 6b. Forecast teaser
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Prakiraan saldo", span = "baru", linkText = "Detail", onLink = onForecast)
                SaldoCard(Modifier.fillMaxWidth(), onClick = onForecast) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color(0xFF00ACC1).copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.TrendingUp, contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF00ACC1),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Akhir ${Dates.monthName(now)} ≈ ${MoneyFormat.format(fc.atMonthEnd, currency)}",
                                fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp, color = pal.ink, style = tnumStyle
                            )
                            Text(
                                if (fc.paydayTs != null)
                                    "Gajian masuk ${Dates.dayMonth(fc.paydayTs)} · aman belanja ${MoneyFormat.formatCompact(fc.safeDaily, currency)}/hari sampai gajian"
                                else
                                    "Aman belanja ${MoneyFormat.formatCompact(fc.safeDaily, currency)}/hari sampai akhir bulan",
                                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight, contentDescription = null,
                            tint = pal.faint, modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        // 7. Expense donut this month
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Pengeluaran bulan ini", linkText = "Statistik", onLink = onStats)
                if (segments.isEmpty()) {
                    SaldoCard(Modifier.fillMaxWidth()) {
                        Text(
                            "Belum ada pengeluaran bulan ini.",
                            fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted
                        )
                    }
                } else {
                    DonutCard(
                        segments = segments,
                        currency = currency,
                        centerLabel = "Total keluar",
                        centerValue = MoneyFormat.formatCompact(expense, currency),
                        selectedIndex = selectedSeg,
                        onDrill = { i ->
                            selectedSeg = i
                            onDrillCategory(donutData[i].first)
                        }
                    )
                }
            }
        }
        // 8. Savings goal strip
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Target tabungan", span = "baru", linkText = "Lihat semua", onLink = onGoals)
                SaldoCard(Modifier.fillMaxWidth()) {
                    if (topGoal == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Belum ada target — buat target pertamamu",
                                fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(pal.accentSoft)
                                    .clickable { onGoals() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Buat", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp, color = pal.accent)
                            }
                        }
                    } else {
                        val g = topGoal
                        val pct = if (g.targetMinor > 0)
                            g.savedMinor.toDouble() / g.targetMinor else 0.0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(
                                pct = pct.toFloat(),
                                color = colorOf(g.colorHex),
                                size = 52.dp, stroke = 9.dp
                            ) {
                                Text(
                                    "${(pct * 100).roundToInt()}%",
                                    fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp, color = pal.ink,
                                    style = tnumStyle
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp, color = pal.ink)
                                Text(
                                    "${MoneyFormat.format(g.savedMinor, currency)} dari ${MoneyFormat.format(g.targetMinor, currency)}",
                                    fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                                    style = tnumStyle
                                )
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(pal.accentSoft)
                                    .clickable { fillGoal = g }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("+ Isi", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp, color = pal.accent)
                            }
                        }
                    }
                }
            }
        }
        // 9. Recent transactions (flat list, one card)
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Transaksi terakhir", linkText = "Lihat semua", onLink = onSeeAllTxns)
                if (recent.isEmpty()) {
                    EmptyState(
                        "Belum ada transaksi",
                        "Ketuk tombol + di bawah untuk mencatat yang pertama."
                    )
                } else {
                    DividerList(
                        items = recent,
                        key = { it.id },
                        modifier = Modifier.fillMaxWidth()
                    ) { t ->
                        TxnRow(
                            txn = t,
                            catMap = catMap,
                            accMap = accMap,
                            currency = currency,
                            onClick = { onTxnClick(t) }
                        )
                    }
                }
            }
        }
    }

    fillGoal?.let { g ->
        GoalFillDialog(vm = vm, goal = g, onClose = { fillGoal = null })
    }
}

@Composable
private fun InOutCard(
    label: String,
    value: Long,
    currency: String,
    isIncome: Boolean,
    hide: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = saldoColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(
                        if (isIncome) pal.successFill.copy(alpha = 0.14f) else pal.accentSoft
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isIncome) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = if (isIncome) pal.successText else pal.accent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp, color = pal.muted)
                Text(
                    if (hide) "••••••" else MoneyFormat.format(value, currency),
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp, color = pal.ink,
                    style = tnumStyle, maxLines = 1
                )
            }
        }
    }
}
