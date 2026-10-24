package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Budget
import com.jiko.moneytracker.data.SavingsGoal
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import java.util.Calendar
import kotlin.math.roundToInt

private data class HealthRow(val ok: Boolean, val text: String)

/** Reports: health score, category donut, 6-month cashflow. */
@Composable
fun StatsScreen(vm: MoneyViewModel, onDrillCategory: (Long) -> Unit) {
    val txns by vm.txns.collectAsState()
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val goals by vm.goals.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    val catMap = remember(categories) { categories.associateBy { it.id } }

    var period by remember { mutableStateOf(1) } // 0 Minggu, 1 Bulan, 2 Tahun
    var anchor by remember { mutableStateOf(System.currentTimeMillis()) }

    fun shift(n: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = anchor }
        when (period) {
            0 -> c.add(Calendar.WEEK_OF_YEAR, n)
            2 -> c.add(Calendar.YEAR, n)
            else -> c.add(Calendar.MONTH, n)
        }
        return c.timeInMillis
    }
    val (from, to) = remember(period, anchor) {
        val c = Calendar.getInstance().apply { timeInMillis = anchor }
        when (period) {
            0 -> {
                c.firstDayOfWeek = Calendar.MONDAY
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                val f = c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 7)
                f to (c.timeInMillis - 1)
            }
            2 -> {
                c.set(Calendar.MONTH, 0); c.set(Calendar.DAY_OF_MONTH, 1)
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                val f = c.timeInMillis
                c.add(Calendar.YEAR, 1)
                f to (c.timeInMillis - 1)
            }
            else -> {
                val cc = Calendar.getInstance().apply { timeInMillis = anchor }
                Dates.monthRange(cc.get(Calendar.YEAR), cc.get(Calendar.MONTH))
            }
        }
    }
    val periodLabel = remember(period, anchor) {
        when (period) {
            0 -> "${Dates.dayLabel(from)} – ${Dates.dayLabel(to)}"
            2 -> Calendar.getInstance().apply { timeInMillis = anchor }
                .get(Calendar.YEAR).toString()
            else -> Dates.monthLabel(anchor).replaceFirstChar { it.uppercase() }
        }
    }
    val periodTxns = remember(txns, from, to) { txns.filter { it.timestamp in from..to } }
    val income = remember(periodTxns) {
        periodTxns.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
    }
    val expense = remember(periodTxns) {
        periodTxns.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
    }

    // Last 6 months for cashflow (always calendar months)
    val trend = remember(txns) {
        val c = Calendar.getInstance()
        (5 downTo 0).map { back ->
            val cc = (c.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
            val (f, t) = Dates.monthRange(cc.get(Calendar.YEAR), cc.get(Calendar.MONTH))
            val inT = txns.filter { it.timestamp in f..t }
            Triple(
                Dates.monthShort(cc.timeInMillis),
                inT.filter { it.type == "INCOME" }.sumOf { it.amountMinor },
                inT.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
            )
        }
    }

    var selectedSeg by remember { mutableStateOf(-1) }
    val donutData = remember(periodTxns, catMap) {
        periodTxns.filter { it.type == "EXPENSE" && it.categoryId != null }
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

    // Health score (web healthScore, over the selected period)
    val topGoal = remember(goals) {
        goals.maxByOrNull { if (it.targetMinor > 0) it.savedMinor.toDouble() / it.targetMinor else 0.0 }
    }
    val (score, verdict, healthRows) = remember(income, expense, budgets, periodTxns, topGoal) {
        healthScore(income, expense, budgets, periodTxns, topGoal)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { TopBar("Statistik") }
        item {
            SegControl(
                options = listOf("Minggu", "Bulan", "Tahun"),
                selected = period,
                onSelect = { period = it },
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        // Period nav
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(Icons.Filled.ChevronLeft, onClick = { anchor = shift(-1) },
                    desc = "Sebelumnya")
                Text(
                    periodLabel,
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp, color = pal.ink,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconBtn(Icons.Filled.ChevronRight, onClick = { anchor = shift(1) },
                    desc = "Berikutnya")
            }
        }
        // Health card
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Skor kesehatan keuangan", span = "baru")
                SaldoCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            pct = score / 100f,
                            color = pal.successFill,
                            size = 74.dp, stroke = 10.dp
                        ) {
                            Text(
                                "$score",
                                fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp, color = pal.ink,
                                style = tnumStyle
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(verdict, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp, color = pal.ink)
                            Spacer(Modifier.height(6.dp))
                            healthRows.forEach { r ->
                                Row(
                                    Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        if (r.ok) Icons.Filled.Check else Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = if (r.ok) pal.successText else pal.warningText,
                                        modifier = Modifier.size(13.dp).padding(top = 2.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(r.text, fontFamily = Jakarta, fontSize = 12.5.sp,
                                        color = pal.muted)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Donut
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Pengeluaran per kategori")
                if (segments.isEmpty()) {
                    EmptyState("Belum ada data", "Belum ada pengeluaran pada periode ini.")
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
        // Cashflow 6 months
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Arus kas 6 bulan")
                SaldoCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(color = pal.successText)) { append("■ ") }
                            append("masuk · ")
                            withStyle(SpanStyle(color = pal.accent)) { append("■ ") }
                            append("keluar")
                        }, fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted)
                    }
                    GroupedBarChart(
                        months = trend.map { it.first },
                        income = trend.map { it.second },
                        expense = trend.map { it.third },
                        incomeColor = pal.successFill,
                        expenseColor = pal.accent,
                        height = 130.dp
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        trend.forEach { (m, _, _) ->
                            Text(m.take(3), fontFamily = Jakarta, fontSize = 10.sp,
                                color = pal.muted, modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val netLine = income - expense
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bersih · ${Dates.monthLabel(anchor).replaceFirstChar { it.uppercase() }}",
                            fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted)
                        Text(
                            (if (netLine >= 0) "+" else "−") +
                                    MoneyFormat.format(kotlin.math.abs(netLine), currency),
                            fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, style = tnumStyle,
                            color = if (netLine >= 0) pal.successText
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/** Web healthScore(): rate from income/expense, penalty per blown budget. */
private fun healthScore(
    income: Long,
    expense: Long,
    budgets: List<Budget>,
    periodTxns: List<com.jiko.moneytracker.data.Txn>,
    topGoal: SavingsGoal?
): Triple<Int, String, List<HealthRow>> {
    val rate = if (income > 0) maxOf(0.0, (income - expense).toDouble() / income) else 0.0
    val over = budgets.count { b ->
        val spent = periodTxns.filter {
            it.type == "EXPENSE" && (b.categoryId == null || it.categoryId == b.categoryId)
        }.sumOf { it.amountMinor }
        b.amountMinor > 0 && spent > b.amountMinor
    }
    val score = (52 + rate * 55 - over * 11).roundToInt().coerceIn(5, 98)
    val verdict = when {
        score >= 75 -> "Keuanganmu sehat"
        score >= 50 -> "Cukup stabil"
        else -> "Perlu perhatian"
    }
    val goalPct = topGoal?.let {
        if (it.targetMinor > 0) (it.savedMinor * 100 / it.targetMinor).toInt() else 0
    }
    val rows = listOf(
        HealthRow(
            rate >= 0.2,
            "Rasio tabungan ${(rate * 100).roundToInt()}% dari pemasukan" +
                    if (rate >= 0.2) " — sehat" else " — idealnya ≥ 20%"
        ),
        HealthRow(
            over == 0,
            if (over == 0) "Tidak ada anggaran yang jebol bulan ini"
            else "$over anggaran melewati batas bulan ini"
        ),
        HealthRow(
            true,
            if (goalPct != null) "Dana darurat terisi $goalPct% dari target"
            else "Belum ada target tabungan — buat dari menu Target Tabungan"
        )
    )
    return Triple(score, verdict, rows)
}
