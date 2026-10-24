package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Recurring
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat

/** Scheduled / recurring transactions. */
@Composable
fun RecurringScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val recurrings by vm.recurrings.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Recurring?>(null) }

    val sorted = remember(recurrings) { recurrings.sortedBy { it.nextDue } }
    val activeCount = remember(recurrings) { recurrings.count { it.active } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            TopBar(
                title = "Transaksi Berulang",
                onBack = onBack,
                trailing = {
                    IconBtn(Icons.Filled.Add, onClick = {
                        editing = null; showEditor = true
                    }, desc = "Tambah berulang")
                }
            )
        }
        item {
            Text(
                "$activeCount jadwal aktif · tagihan dan pemasukan rutin. Yang bertanda otomatis tercatat sendiri saat jatuh tempo.",
                fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        item {
            if (sorted.isEmpty()) {
                EmptyState(
                    title = "Belum ada transaksi berulang",
                    sub = "Tambahkan tagihan atau pemasukan rutin lewat tombol +.",
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            } else {
                DividerList(
                    items = sorted,
                    key = { it.id },
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) { r ->
                    RecurringRow(
                        vm = vm, r = r, currency = currency,
                        catName = r.categoryId?.let { catMap[it]?.name } ?: "Berulang",
                        catIcon = r.categoryId?.let { catMap[it]?.iconKey } ?: "autorenew",
                        catColor = r.categoryId?.let { catMap[it]?.colorHex } ?: "#9E9E9E",
                        accName = accMap[r.accountId]?.name ?: "",
                        onEdit = { editing = r; showEditor = true }
                    )
                }
            }
        }
    }

    if (showEditor) {
        RecurringEditor(vm = vm, editing = editing, onClose = { showEditor = false })
    }
}

@Composable
private fun RecurringRow(
    vm: MoneyViewModel,
    r: Recurring,
    currency: String,
    catName: String,
    catIcon: String,
    catColor: String,
    accName: String,
    onEdit: () -> Unit
) {
    val pal = saldoColors
    val due = r.nextDue <= System.currentTimeMillis()
    Column(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(catIcon, catColor, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.note.ifBlank { catName },
                    fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = pal.ink
                )
                Text(
                    "${cadenceLabel(r.cadence)} · berikutnya ${Dates.dayLabel(r.nextDue)} · " +
                            if (r.autoCreate) "otomatis" else "konfirmasi dulu",
                    fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (r.type == "INCOME") "+" else "−"}${MoneyFormat.format(r.amountMinor, currency)}",
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    style = tnumStyle,
                    color = if (r.type == "INCOME") pal.successText else pal.ink
                )
                Text(accName, fontFamily = Jakarta, fontSize = 11.sp, color = pal.faint)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (due) "Jatuh tempo: ${Dates.dayLabel(r.nextDue)}"
                else "Berikutnya: ${Dates.dayLabel(r.nextDue)}",
                fontFamily = Jakarta, fontSize = 12.sp,
                color = if (due) pal.accent else pal.muted,
                modifier = Modifier.weight(1f)
            )
            if (due && !r.autoCreate) {
                TextButton(onClick = { vm.applyRecurringNow(r) }) {
                    Text("Terapkan", fontSize = 12.sp)
                }
                TextButton(onClick = { vm.skipRecurring(r) }) {
                    Text("Lewati", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onEdit) { Text("Ubah", fontSize = 12.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (r.active) "Aktif" else "Nonaktif",
                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted,
                modifier = Modifier.weight(1f)
            )
            SaldoSwitch(checked = r.active, onChecked = {
                vm.saveRecurring(r.copy(active = it), true)
            })
        }
    }
}

private fun cadenceLabel(c: String) = when (c) {
    "DAILY" -> "Harian"
    "WEEKLY" -> "Mingguan"
    "YEARLY" -> "Tahunan"
    else -> "Bulanan"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditor(vm: MoneyViewModel, editing: Recurring?, onClose: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()

    var type by remember { mutableStateOf(editing?.type ?: "EXPENSE") }
    var amountText by remember { mutableStateOf(editing?.let { MoneyFormat.minorToDigits(it.amountMinor) } ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var accountId by remember { mutableStateOf(editing?.accountId ?: accounts.firstOrNull()?.id ?: -1) }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var cadence by remember { mutableStateOf(editing?.cadence ?: "MONTHLY") }
    var autoCreate by remember { mutableStateOf(editing?.autoCreate ?: true) }
    var catMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val visibleCats = remember(categories, type) {
        categories.filter { if (type == "INCOME") it.kind != "EXPENSE" else it.kind != "INCOME" }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (editing == null) "Berulang Baru" else "Ubah Berulang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("EXPENSE" to "Keluar", "INCOME" to "Masuk").forEach { (v, l) ->
                        SegmentedButton(
                            selected = type == v,
                            onClick = { type = v },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            label = { Text(l) }
                        )
                    }
                }
                OutlinedTextField(amountText, { amountText = it.filter { c -> c.isDigit() }.take(15) },
                    label = { Text("Jumlah (${MoneyFormat.symbol(currency)})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton({ catMenu = true }, Modifier.fillMaxWidth()) {
                        Text(categoryId?.let { catMap[it]?.name } ?: "Pilih kategori")
                    }
                    DropdownMenu(catMenu, { catMenu = false }) {
                        visibleCats.forEach { c ->
                            DropdownMenuItem({ Text(c.name) },
                                { categoryId = c.id; catMenu = false })
                        }
                    }
                }
                var accMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ accMenu = true }, Modifier.fillMaxWidth()) {
                        Text(accounts.firstOrNull { it.id == accountId }?.name ?: "Pilih akun")
                    }
                    DropdownMenu(accMenu, { accMenu = false }) {
                        accounts.forEach { a ->
                            DropdownMenuItem({ Text(a.name) },
                                { accountId = a.id; accMenu = false })
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Catatan") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { c ->
                        FilterChip(selected = c == cadence, onClick = { cadence = c },
                            label = { Text(cadenceLabel(c)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Otomatis terapkan", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    SaldoSwitch(autoCreate, { autoCreate = it })
                }
                if (editing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hapus", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toLongOrNull() ?: 0L
                if (amt <= 0 || accountId < 0) { vm.notify("Lengkapi data"); return@TextButton }
                vm.saveRecurring(
                    Recurring(
                        id = editing?.id ?: 0, type = type, amountMinor = amt,
                        categoryId = categoryId, accountId = accountId, note = note.trim(),
                        cadence = cadence, autoCreate = autoCreate,
                        nextDue = editing?.nextDue ?: System.currentTimeMillis(),
                        active = editing?.active ?: true
                    ), editing != null
                )
                onClose()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteRecurring(editing); onClose() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}
