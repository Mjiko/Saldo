package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.MoneyFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** "More" menu: budgets, calendar, recurring, goals, accounts, settings. */
@Composable
fun MoreScreen(
    vm: MoneyViewModel,
    onBudgets: () -> Unit,
    onCalendar: () -> Unit,
    onRecurring: () -> Unit,
    onGoals: () -> Unit,
    onAccounts: () -> Unit,
    onSettings: () -> Unit,
    onScanReceipt: () -> Unit,
    onForecast: () -> Unit,
    onReports: () -> Unit,
    onDebts: () -> Unit,
) {
    val recurrings by vm.recurrings.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors

    val balances by remember(accounts) {
        if (accounts.isEmpty()) flowOf(emptyMap<Long, Long>())
        else combine(accounts.map { vm.balanceOf(it.id) }) { arr ->
            accounts.mapIndexed { i, a -> a.id to arr[i] }.toMap()
        }
    }.collectAsState(initial = emptyMap())
    val total = remember(balances) { balances.values.sum() }
    val activeRecurring = remember(recurrings) { recurrings.count { it.active } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { TopBar("Lainnya") }
        item {
            DividerList(
                items = listOf(
                    MenuItem(Icons.Filled.Adjust, Color(0xFFFF5722),
                        "Anggaran", "Batas bulanan per kategori", null, onBudgets),
                    MenuItem(Icons.Filled.CalendarMonth, Color(0xFF00B0FF),
                        "Kalender", "Pengeluaran per tanggal", null, onCalendar),
                    MenuItem(Icons.Filled.Repeat, Color(0xFF7C4DFF),
                        "Transaksi Berulang", "$activeRecurring jadwal aktif", null, onRecurring),
                    MenuItem(Icons.Filled.AutoAwesome, Color(0xFF00C853),
                        "Target Tabungan", "Baru — nabung per tujuan", "BARU", onGoals),
                    MenuItem(Icons.Filled.DocumentScanner, Color(0xFFD6339E),
                        "Pindai Struk", "Foto struk, nominal terbaca otomatis", "BARU", onScanReceipt),
                    MenuItem(Icons.Filled.TrendingUp, Color(0xFF00ACC1),
                        "Prakiraan Saldo", "Proyeksi saldo sampai akhir bulan depan", "BARU", onForecast),
                    MenuItem(Icons.Filled.Description, Color(0xFFD9A404),
                        "Laporan Bulanan", "Ringkasan, perbandingan & unduh laporan", "BARU", onReports),
                    MenuItem(Icons.Filled.SwapHoriz, Color(0xFF00BFA5),
                        "Hutang & Piutang", "Catat siapa berutang & siapa kamu utangi", "BARU", onDebts),
                    MenuItem(Icons.Filled.Wallet, Color(0xFF3D5AFE),
                        "Akun & Dompet",
                        "${accounts.size} akun · ${MoneyFormat.formatCompact(total, currency)}",
                        null, onAccounts),
                    MenuItem(Icons.Filled.Settings, Color(0xFF5A6472),
                        "Pengaturan", "Tema, kunci PIN, ekspor & cadangan", null, onSettings),
                ),
                key = { it.name },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) { m ->
                MenuRow(
                    icon = m.icon, iconBg = m.color, name = m.name,
                    desc = m.desc, badge = m.badge, onClick = m.onClick
                )
            }
        }
        item {
            SaldoCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text("Saldo", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = pal.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pelacak pengeluaran & anggaran yang minimalis, sepenuhnya luring, tanpa akun. Versi 1.7",
                    fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                )
            }
        }
    }
}

private data class MenuItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val name: String,
    val desc: String,
    val badge: String?,
    val onClick: () -> Unit
)
