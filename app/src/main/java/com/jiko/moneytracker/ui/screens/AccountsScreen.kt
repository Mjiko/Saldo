package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Account
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.MoneyFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Wallets/accounts with live balances; transfers live in the Add sheet. */
@Composable
fun AccountsScreen(vm: MoneyViewModel, onAccountTxns: (Long) -> Unit, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val txns by vm.txns.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }

    val balances by remember(accounts) {
        if (accounts.isEmpty()) flowOf(emptyMap<Long, Long>())
        else combine(accounts.map { vm.balanceOf(it.id) }) { arr ->
            accounts.mapIndexed { i, a -> a.id to arr[i] }.toMap()
        }
    }.collectAsState(initial = emptyMap())
    val total = remember(balances) { balances.values.sum() }
    val txnCount = remember(txns) {
        txns.groupingBy { it.accountId }.eachCount()
    }
    val txnToCount = remember(txns) {
        txns.mapNotNull { it.toAccountId }.groupingBy { it }.eachCount()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            TopBar(
                title = "Akun & Dompet",
                onBack = onBack,
                trailing = {
                    IconBtn(Icons.Filled.Add, onClick = {
                        editing = null; showEditor = true
                    }, desc = "Tambah akun")
                }
            )
        }
        // Total card
        item {
            SaldoCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("Total semua akun", fontFamily = Jakarta, fontSize = 12.5.sp,
                    color = pal.muted)
                Spacer(Modifier.height(2.dp))
                Text(
                    MoneyFormat.format(total, currency),
                    fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp, letterSpacing = (-0.52).sp, color = pal.ink,
                    style = tnumStyle
                )
            }
        }
        // Account rows
        item {
            DividerList(
                items = accounts,
                key = { it.id },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) { a ->
                val cnt = (txnCount[a.id] ?: 0) + (txnToCount[a.id] ?: 0)
                MenuRow(
                    icon = iconFor(a.iconKey),
                    iconBg = colorOf(a.colorHex),
                    name = a.name,
                    desc = "$cnt transaksi",
                    right = {
                        Text(
                            MoneyFormat.format(balances[a.id] ?: 0L, currency),
                            fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = pal.ink,
                            style = tnumStyle
                        )
                    },
                    onClick = { onAccountTxns(a.id) }
                )
            }
        }
        item {
            Text(
                "Ketuk akun untuk melihat transaksinya. Transfer antar akun tersedia lewat tombol + di bawah.",
                fontFamily = Jakarta, fontSize = 12.sp, color = pal.faint,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }

    if (showEditor) {
        AccountEditor(vm = vm, editing = editing, onClose = { showEditor = false })
    }
}

@Composable
fun AccountEditor(vm: MoneyViewModel, editing: Account?, onClose: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var iconKey by remember { mutableStateOf(editing?.iconKey ?: "wallet") }
    var colorHex by remember { mutableStateOf(editing?.colorHex ?: "#FF5722") }
    var iconMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (editing == null) "Akun Baru" else "Ubah Akun") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama akun") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(iconKey, colorHex, size = 44)
                    Spacer(Modifier.width(12.dp))
                    Box {
                        OutlinedButton(onClick = { iconMenu = true }) { Text("Ikon") }
                        DropdownMenu(iconMenu, { iconMenu = false }) {
                            listOf("wallet", "bank", "phone", "cash", "card", "save").forEach { k ->
                                DropdownMenuItem({
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CategoryIcon(k, colorHex, size = 32)
                                        Spacer(Modifier.width(8.dp)); Text(k)
                                    }
                                }, { iconKey = k; iconMenu = false })
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORY_COLOR_CHOICES.take(8).forEach { hex ->
                        IconButton(onClick = { colorHex = hex },
                            modifier = Modifier.size(36.dp)) {
                            CategoryIcon(iconKey, hex, size = 32)
                        }
                    }
                }
                if (editing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hapus akun", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { vm.notify("Isi nama akun"); return@TextButton }
                vm.saveAccount(
                    Account(id = editing?.id ?: 0, name = name.trim(),
                        iconKey = iconKey, colorHex = colorHex,
                        sortOrder = editing?.sortOrder ?: 0),
                    editing != null
                )
                onClose()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus akun?") },
            text = { Text("Transaksi di akun ini ikut terhapus.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAccount(editing); onClose() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}
