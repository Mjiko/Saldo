package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Txn
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import java.util.Calendar

/** Month calendar (Monday start) with daily expense/income totals. */
@Composable
fun CalendarScreen(vm: MoneyViewModel, onTxnClick: (Txn) -> Unit, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    val todayStart = remember { Dates.startOfDay(System.currentTimeMillis()) }
    var monthAnchor by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedDay by remember { mutableStateOf(todayStart) }

    // Cells: leading prev-month numbers, then month days (epoch millis of each day).
    val cells: List<Pair<Long, Boolean>> = remember(monthAnchor) {
        val c = Calendar.getInstance().apply { timeInMillis = monthAnchor }
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val lead = (c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
        val dim = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val list = mutableListOf<Pair<Long, Boolean>>()
        val pc = Calendar.getInstance().apply { timeInMillis = c.timeInMillis; add(Calendar.MONTH, -1) }
        val prevDim = pc.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in (prevDim - lead + 1)..prevDim) {
            val cc = Calendar.getInstance()
            cc.set(pc.get(Calendar.YEAR), pc.get(Calendar.MONTH), d, 0, 0, 0)
            cc.set(Calendar.MILLISECOND, 0)
            list.add(cc.timeInMillis to false)
        }
        for (d in 1..dim) {
            val cc = Calendar.getInstance()
            cc.set(y, m, d, 0, 0, 0); cc.set(Calendar.MILLISECOND, 0)
            list.add(cc.timeInMillis to true)
        }
        list
    }

    val byDay = remember(txns) { txns.groupBy { Dates.startOfDay(it.timestamp) } }
    val sumsByDay = remember(byDay) {
        byDay.mapValues { (_, list) ->
            list.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor } to
                    list.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
        }
    }
    val (mFrom, mTo) = remember(monthAnchor) {
        val c = Calendar.getInstance().apply { timeInMillis = monthAnchor }
        Dates.monthRange(c.get(Calendar.YEAR), c.get(Calendar.MONTH))
    }
    val monthExpense = remember(txns, mFrom, mTo) {
        txns.filter { it.type == "EXPENSE" && it.timestamp in mFrom..mTo }.sumOf { it.amountMinor }
    }
    val selectedTxns = remember(selectedDay, byDay) { byDay[selectedDay].orEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { TopBar("Kalender", onBack = onBack) }
        item {
            SaldoCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                // cal-head
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Filled.ChevronLeft, onClick = {
                        monthAnchor = Dates.addMonths(monthAnchor, -1)
                    }, desc = "Sebelumnya")
                    Text(
                        Dates.monthLabel(monthAnchor).replaceFirstChar { it.uppercase() },
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp, color = pal.ink,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    IconBtn(Icons.Filled.ChevronRight, onClick = {
                        monthAnchor = Dates.addMonths(monthAnchor, 1)
                    }, desc = "Berikutnya")
                }
                Spacer(Modifier.height(10.dp))
                // Weekday header, Monday start
                Row(Modifier.fillMaxWidth()) {
                    listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontFamily = Jakarta, fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold, color = pal.faint)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(308.dp),
                    userScrollEnabled = false
                ) {
                    items(cells, key = { it.first }) { (day, inMonth) ->
                        val sums = sumsByDay[day]
                        val e = sums?.first ?: 0L
                        val iAmt = sums?.second ?: 0L
                        val isSel = selectedDay == day && inMonth
                        val isToday = day == todayStart && inMonth
                        Column(
                            Modifier.aspectRatio(0.85f).padding(2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSel) pal.accent.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = inMonth) { selectedDay = day }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                if (isToday) Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(pal.accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                else Modifier.padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    Calendar.getInstance().apply { timeInMillis = day }
                                        .get(Calendar.DAY_OF_MONTH).toString(),
                                    fontFamily = Jakarta, fontSize = 14.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        !inMonth -> pal.faint
                                        isToday -> pal.accent
                                        else -> pal.ink
                                    }
                                )
                            }
                            if (inMonth && (e > 0 || iAmt > 0)) {
                                Text(
                                    when {
                                        e > 0 -> "−" + MoneyFormat.formatCompact(e, currency)
                                            .removePrefix(MoneyFormat.symbol(currency))
                                        else -> "+" + MoneyFormat.formatCompact(iAmt, currency)
                                            .removePrefix(MoneyFormat.symbol(currency))
                                    },
                                    fontFamily = Jakarta, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (e > 0) pal.accent else pal.successText,
                                    style = tnumStyle, maxLines = 1
                                )
                            }
                        }
                    }
                }
                // netline
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total keluar bulan ini", fontFamily = Jakarta, fontSize = 12.5.sp,
                        color = pal.muted)
                    Text(
                        MoneyFormat.format(monthExpense, currency),
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = pal.ink, style = tnumStyle
                    )
                }
            }
        }
        // Selected day
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle(Dates.dayShort(selectedDay))
                if (selectedTxns.isEmpty()) {
                    EmptyState(
                        "Tidak ada transaksi",
                        "Belum ada catatan pada tanggal ini."
                    )
                } else {
                    DividerList(
                        items = selectedTxns,
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
}
