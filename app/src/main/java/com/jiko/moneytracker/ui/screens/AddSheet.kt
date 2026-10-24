package com.jiko.moneytracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Category
import com.jiko.moneytracker.data.Txn
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import java.util.Calendar

/** Add/Edit transaction bottom sheet. Amount-first, ≤3 taps to save. */
@Composable
fun AddSheet(
    vm: MoneyViewModel,
    editing: Txn?,
    presetCategoryId: Long = -1,
    onClose: () -> Unit,
    onScanReceipt: (() -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
) {
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors

    var type by remember(editing) { mutableStateOf(editing?.type ?: "EXPENSE") }
    var categoryId by remember(editing) {
        mutableStateOf(editing?.categoryId ?: presetCategoryId.takeIf { it > 0 })
    }
    var accountId by remember(editing, accounts) {
        mutableStateOf(
            editing?.accountId
                ?: vm.prefs.lastAccountId.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id ?: -1
        )
    }
    var toAccountId by remember(editing, accounts) {
        mutableStateOf(
            editing?.toAccountId
                ?: accounts.firstOrNull { it.id != accountId }?.id ?: -1
        )
    }
    var note by remember(editing) { mutableStateOf(editing?.note ?: "") }
    var tags by remember(editing) { mutableStateOf(editing?.tags ?: "") }
    var dateTs by remember(editing) { mutableStateOf(editing?.timestamp ?: System.currentTimeMillis()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showReceiptFull by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val catMap = remember(categories) { categories.associateBy { it.id } }

    // Smart default: category remembered per note
    LaunchedEffect(note) {
        if (editing == null && note.isNotBlank() && type != "TRANSFER") {
            val remembered = vm.prefs.categoryForNote(note)
            if (remembered > 0 && catMap.containsKey(remembered)) categoryId = remembered
        }
    }
    val repeats = remember { vm.prefs.topRepeats() }

    val visibleCats = remember(categories, type) {
        categories.filter {
            when (type) {
                "EXPENSE" -> it.kind != "INCOME"
                "INCOME" -> it.kind != "EXPENSE"
                else -> true
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (editing == null) "Tambah Transaksi" else "Ubah Transaksi",
                fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                color = pal.ink
            )
            Row {
                if (editing != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Hapus",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup",
                        tint = pal.muted)
                }
            }
        }

        // Receipt scan entry (web #aScan, create mode only)
        if (editing == null && onScanReceipt != null) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(pal.accentSoft)
                    .clickable { onScanReceipt() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null,
                        tint = pal.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pindai struk belanja", fontFamily = Jakarta,
                        fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = pal.accent)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(pal.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("BARU", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, color = pal.accentInk)
                    }
                }
            }
        }

        // Type toggle (web TypeSeg)
        TypeSeg(
            type = type,
            onType = { type = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (type == "TRANSFER") {
            TransferPickers(
                accounts = accounts, fromId = accountId, toId = toAccountId,
                onFrom = { accountId = it }, onTo = { toAccountId = it },
                onSwap = { val t = accountId; accountId = toAccountId; toAccountId = t }
            )
        } else {
            // Smart repeat suggestions
            if (editing == null && repeats.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeats.forEach { (rNote, amt) ->
                        AssistChip(
                            onClick = {
                                note = rNote
                                // save immediately: amount + remembered category
                                val cat = vm.prefs.categoryForNote(rNote)
                                vm.saveTxn(
                                    Txn(type = type, amountMinor = amt, accountId = accountId,
                                        categoryId = cat.takeIf { it > 0 }, note = rNote,
                                        timestamp = dateTs), false
                                )
                                onClose()
                            },
                            label = { Text("$rNote · ${MoneyFormat.formatCompact(amt, currency)}", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Filled.Bolt, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
            // Category grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(horizontal = 8.dp),
                userScrollEnabled = true
            ) {
                items(visibleCats, key = { it.id }) { c ->
                    CategoryCell(c, selected = categoryId == c.id, onClick = { categoryId = c.id })
                }
                if (onManageCategories != null) {
                    item(key = "kelola") {
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .clickable { onManageCategories() }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(42.dp)
                                    .clip(RoundedCornerShape((42 * 0.325f).dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = "Kelola kategori",
                                    tint = pal.muted, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Kelola", fontFamily = Jakarta, fontSize = 11.sp,
                                textAlign = TextAlign.Center, maxLines = 2, color = pal.muted)
                        }
                    }
                }
            }
            // Account picker
            Text("Akun", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { a ->
                    FilterChip(
                        selected = accountId == a.id,
                        onClick = { accountId = a.id },
                        label = { Text(a.name, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(iconFor(a.iconKey), null, Modifier.size(16.dp),
                                tint = colorOf(a.colorHex))
                        }
                    )
                }
            }
        }

        // Date + note + tags
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = dateTs }
                    DatePickerDialog(context, { _, y, m, d ->
                        val nc = Calendar.getInstance()
                        nc.set(y, m, d,
                            Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                            Calendar.getInstance().get(Calendar.MINUTE))
                        dateTs = nc.timeInMillis
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(Dates.dayLabel(dateTs), fontSize = 13.sp)
            }
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("Catatan") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tags, onValueChange = { tags = it },
            label = { Text("Tag (pisahkan koma)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        // Attached receipt photo (detail) — tap for full view
        editing?.receiptPath?.let { path ->
            Text("Struk terlampir", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            ReceiptImage(
                path = path,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .height(170.dp).clip(RoundedCornerShape(12.dp))
                    .clickable { showReceiptFull = true },
                maxDim = 700,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
        }

        // Keypad with Save inside
        Keypad(
            initialMinor = editing?.amountMinor ?: 0,
            currency = currency,
            modifier = Modifier.fillMaxWidth(),
            onSave = { amount ->
                if (amount <= 0) { vm.notify("Masukkan jumlah dulu"); return@Keypad }
                if (accountId < 0) { vm.notify("Pilih akun dulu"); return@Keypad }
                if (type == "TRANSFER" && (toAccountId < 0 || toAccountId == accountId)) {
                    vm.notify("Pilih akun tujuan yang berbeda"); return@Keypad
                }
                if (type != "TRANSFER" && categoryId == null) {
                    vm.notify("Pilih kategori dulu"); return@Keypad
                }
                val t = Txn(
                    id = editing?.id ?: 0,
                    type = type,
                    amountMinor = amount,
                    accountId = accountId,
                    toAccountId = if (type == "TRANSFER") toAccountId else null,
                    categoryId = if (type == "TRANSFER") null else categoryId,
                    note = note.trim(), tags = tags.trim(),
                    timestamp = dateTs,
                    recurringId = editing?.recurringId,
                    receiptPath = editing?.receiptPath
                )
                vm.saveTxn(t, editing != null)
                onClose()
            }
        )
        Spacer(Modifier.height(12.dp))
    }

    if (showReceiptFull && editing?.receiptPath != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showReceiptFull = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                    .clickable { showReceiptFull = false },
                contentAlignment = Alignment.Center
            ) {
                ReceiptImage(
                    path = editing.receiptPath!!,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    maxDim = 2048,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                IconButton(
                    onClick = { showReceiptFull = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup",
                        tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus transaksi?") },
            text = { Text("Transaksi ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTxns(listOf(editing.id)); onClose()
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun CategoryCell(c: Category, selected: Boolean, onClick: () -> Unit) {
    val pal = saldoColors
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) pal.accent.copy(alpha = 0.14f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CategoryIcon(c.iconKey, c.colorHex, size = 42)
        Spacer(Modifier.height(4.dp))
        Text(c.name, fontFamily = Jakarta, fontSize = 11.sp, textAlign = TextAlign.Center,
            maxLines = 2,
            color = if (selected) pal.accent else pal.ink)
    }
}

@Composable
private fun TransferPickers(
    accounts: List<com.jiko.moneytracker.data.Account>,
    fromId: Long, toId: Long,
    onFrom: (Long) -> Unit, onTo: (Long) -> Unit, onSwap: () -> Unit
) {
    val pal = saldoColors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Dari", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = 12.sp, color = pal.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a ->
                FilterChip(selected = a.id == fromId, onClick = { onFrom(a.id) },
                    label = { Text(a.name, fontSize = 13.sp) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ke", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = pal.muted, modifier = Modifier.weight(1f))
            IconButton(onClick = onSwap) {
                Icon(Icons.Filled.SwapVert, contentDescription = "Tukar")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a ->
                FilterChip(selected = a.id == toId, onClick = { onTo(a.id) },
                    label = { Text(a.name, fontSize = 13.sp) })
            }
        }
    }
}
