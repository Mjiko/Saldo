package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/** Ledger: search + filters + batch select/delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    vm: MoneyViewModel,
    onTxnClick: (Txn) -> Unit,
    onAdd: () -> Unit,
) {
    val txns by vm.txns.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val drill by vm.drillCategory.collectAsState()
    val drillAcc by vm.drillAccount.collectAsState()
    val pal = saldoColors

    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("ALL") }
    var accountFilter by remember { mutableStateOf(-1L) }
    var categoryFilter by remember { mutableStateOf(-1L) }
    val selected = remember { mutableStateListOf<Long>() }

    LaunchedEffect(drill) {
        drill?.let { categoryFilter = it; vm.clearDrill() }
    }
    LaunchedEffect(drillAcc) {
        drillAcc?.let { accountFilter = it; vm.clearAccountDrill() }
    }

    val filtered = remember(txns, query, typeFilter, accountFilter, categoryFilter) {
        txns.filter { t ->
            (query.isBlank() || t.note.contains(query, true) || t.tags.contains(query, true)
                    || t.amountMinor.toString().contains(query)
                    || (t.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
                        ?.contains(query, true) == true)) &&
            (typeFilter == "ALL" || t.type == typeFilter) &&
            (accountFilter < 0 || t.accountId == accountFilter || t.toAccountId == accountFilter) &&
            (categoryFilter < 0 || t.categoryId == categoryFilter)
        }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { Dates.startOfDay(it.timestamp) }.toSortedMap(reverseOrder())
    }
    val dayNets = remember(grouped) {
        grouped.mapValues { (_, list) ->
            list.filter { it.type == "INCOME" }.sumOf { it.amountMinor } -
                    list.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
        }
    }
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // 1. TopBar + batch actions
        item {
            TopBar(
                title = if (selected.isEmpty()) "Transaksi" else "${selected.size} dipilih",
                sub = "${filtered.size} catatan",
                trailing = {
                    if (selected.isNotEmpty()) {
                        IconBtn(Icons.Filled.ContentCopy, onClick = {
                            txns.firstOrNull { it.id == selected.first() }?.let {
                                vm.duplicateTxn(it)
                            }
                            selected.clear()
                        }, desc = "Duplikasi")
                        Spacer(Modifier.width(8.dp))
                        IconBtn(Icons.Filled.Delete, onClick = {
                            vm.deleteTxns(selected.toList()); selected.clear()
                        }, desc = "Hapus")
                    }
                }
            )
        }
        // 2. Search
        item {
            SearchBar(
                query = query,
                onQuery = { query = it },
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
        // 3. Filter chips (wrap)
        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "Semua", "EXPENSE" to "Pengeluaran",
                    "INCOME" to "Pemasukan", "TRANSFER" to "Transfer").forEach { (v, l) ->
                    SaldoFilterChip(label = l, selected = typeFilter == v,
                        onClick = { typeFilter = v })
                }
                if (categoryFilter > 0) {
                    SaldoFilterChip(
                        label = "${catMap[categoryFilter]?.name ?: ""} ✕",
                        selected = true,
                        onClick = { categoryFilter = -1 }
                    )
                }
                if (accountFilter > 0) {
                    SaldoFilterChip(
                        label = "${accMap[accountFilter]?.name ?: ""} ✕",
                        selected = true,
                        onClick = { accountFilter = -1 }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // 4. Day groups
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = "Tidak ada transaksi",
                    sub = "Coba ubah kata kunci atau filter yang aktif.",
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        } else {
            grouped.forEach { (day, list) ->
                val net = dayNets[day] ?: 0L
                item(key = "h$day") {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            Dates.dayShort(day).uppercase(),
                            fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, letterSpacing = 0.48.sp, color = pal.muted
                        )
                        if (net != 0L) {
                            Text(
                                (if (net > 0) "+" else "−") +
                                        MoneyFormat.format(kotlin.math.abs(net), currency),
                                fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp, color = pal.ink,
                                style = tnumStyle
                            )
                        }
                    }
                }
                item(key = "l$day") {
                    DividerList(
                        items = list,
                        key = { it.id },
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) { t ->
                        TxnRow(
                            txn = t,
                            catMap = catMap,
                            accMap = accMap,
                            currency = currency,
                            onClick = { onTxnClick(t) },
                            selectionMode = selected.isNotEmpty(),
                            selected = selected.contains(t.id),
                            onToggleSelect = {
                                if (selected.contains(t.id)) selected.remove(t.id)
                                else selected.add(t.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
