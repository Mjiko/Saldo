package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Budget
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import kotlin.math.min

/** Per-category monthly budgets with progress + over-budget alerts. */
@Composable
fun BudgetsScreen(vm: MoneyViewModel) {
    val budgets by vm.budgets.collectAsState()
    val categories by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val currency by vm.currency.collectAsState()
    val monthStart by vm.monthStart.collectAsState()
    val pal = saldoColors
    val catMap = remember(categories) { categories.associateBy { it.id } }

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Budget?>(null) }

    val now = System.currentTimeMillis()
    val (from, to) = remember(monthStart) { Dates.budgetPeriod(now, monthStart) }

    val spentOf = remember(txns, budgets, from, to) {
        budgets.associate { b ->
            b.id to txns.filter {
                it.type == "EXPENSE" && it.timestamp in from..to &&
                        (b.categoryId == null || it.categoryId == b.categoryId)
            }.sumOf { it.amountMinor }
        }
    }
    val totalLimit = remember(budgets) { budgets.sumOf { it.amountMinor } }
    val totalSpent = remember(spentOf) { spentOf.values.sum() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            TopBar(
                title = "Anggaran",
                trailing = {
                    IconBtn(Icons.Filled.Add, onClick = {
                        editing = null; showEditor = true
                    }, desc = "Tambah anggaran")
                }
            )
        }
        item {
            Text(
                "${Dates.monthLabel(now).replaceFirstChar { it.uppercase() }} · periode mulai tanggal $monthStart · peringatan di 75% · 90% · 100%",
                fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        // Total card
        item {
            SaldoCard(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("Total terpakai", fontFamily = Jakarta,
                        fontWeight = FontWeight.Medium, fontSize = 13.sp, color = pal.muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            MoneyFormat.format(totalSpent, currency),
                            fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp, color = pal.ink,
                            style = tnumStyle
                        )
                        Text(
                            " / ${MoneyFormat.formatCompact(totalLimit, currency)}",
                            fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp, color = pal.muted,
                            style = tnumStyle,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    progress = {
                        if (totalLimit > 0) min(1f, totalSpent.toFloat() / totalLimit) else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(99.dp)),
                    color = pal.accent,
                    trackColor = pal.surface3,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        // Budget rows
        item {
            if (budgets.isEmpty()) {
                EmptyState(
                    title = "Belum ada anggaran",
                    sub = "Buat anggaran per kategori untuk memantau batas bulanan.",
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            } else {
                DividerList(
                    items = budgets,
                    key = { it.id },
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) { b ->
                    BudgetRow(
                        budget = b,
                        cat = b.categoryId?.let { catMap[it] },
                        spent = spentOf[b.id] ?: 0L,
                        currency = currency,
                        onClick = { editing = b; showEditor = true }
                    )
                }
            }
        }
        item {
            Text(
                "Ketuk anggaran untuk mengubah batas. Peringatan muncul di Beranda saat pemakaian menyentuh 75%.",
                fontFamily = Jakarta, fontSize = 12.sp, color = pal.faint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }

    if (showEditor) {
        BudgetEditor(vm = vm, editing = editing, onClose = { showEditor = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditor(vm: MoneyViewModel, editing: Budget?, onClose: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val currency by vm.currency.collectAsState()
    val monthStart by vm.monthStart.collectAsState()

    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var amountText by remember {
        mutableStateOf(editing?.let { MoneyFormat.minorToDigits(it.amountMinor) } ?: "")
    }
    var startDay by remember { mutableStateOf(editing?.monthStartDay ?: monthStart) }
    var alertPct by remember { mutableStateOf(editing?.alertPct ?: 90) }
    var confirmDelete by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    val catMap = remember(categories) { categories.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (editing == null) "Budget Baru" else "Ubah Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { catMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(categoryId?.let { catMap[it]?.name } ?: "Semua pengeluaran")
                    }
                    DropdownMenu(catMenu, { catMenu = false }) {
                        DropdownMenuItem({ Text("Semua pengeluaran") },
                            { categoryId = null; catMenu = false })
                        categories.filter { it.kind != "INCOME" }.forEach { c ->
                            DropdownMenuItem({ Text(c.name) },
                                { categoryId = c.id; catMenu = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() }.take(15) },
                    label = { Text("Batas (${MoneyFormat.symbol(currency)})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mulai tanggal", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    listOf(1, 15, 25).forEach { d ->
                        FilterChip(selected = d == startDay, onClick = { startDay = d },
                            label = { Text("$d") }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Peringatan di", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    listOf(75, 90, 100).forEach { p ->
                        FilterChip(selected = p == alertPct, onClick = { alertPct = p },
                            label = { Text("$p%") }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                if (editing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hapus budget", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toLongOrNull() ?: 0L
                if (amt <= 0) { vm.notify("Masukkan batas budget"); return@TextButton }
                val b = Budget(
                    id = editing?.id ?: 0, categoryId = categoryId,
                    amountMinor = amt, monthStartDay = startDay, alertPct = alertPct
                )
                vm.saveBudget(b, editing != null)
                onClose()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus budget?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteBudget(editing); onClose() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}
