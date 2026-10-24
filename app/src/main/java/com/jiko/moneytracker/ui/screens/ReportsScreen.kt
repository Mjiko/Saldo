package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Laporan Bulanan — monthly report, mirroring the web reports screen. */
@Composable
fun ReportsScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors

    val nowCal = remember { Calendar.getInstance() }
    var ymKey by rememberSaveable {
        mutableStateOf(nowCal.get(Calendar.YEAR) * 12 + nowCal.get(Calendar.MONTH))
    }
    val year = ymKey / 12
    val month0 = ymKey % 12

    val catMap = remember(categories) { categories.associateBy { it.id } }
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    fun rollup(catId: Long?): Category? {
        val c = catId?.let { catMap[it] } ?: return null
        return c.parentId?.let { catMap[it] } ?: c
    }

    val (from, to) = remember(year, month0) { Dates.monthRange(year, month0) }
    val (pFrom, pTo) = remember(year, month0) {
        val prev = Calendar.getInstance().apply { set(year, month0 - 1, 1) }
        Dates.monthRange(prev.get(Calendar.YEAR), prev.get(Calendar.MONTH))
    }
    val list = remember(txns, from, to) {
        txns.filter { it.timestamp in from..to }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
    }
    val prevList = remember(txns, pFrom, pTo) { txns.filter { it.timestamp in pFrom..pTo } }

    val inc = remember(list) { list.filter { it.type == "INCOME" }.sumOf { it.amountMinor } }
    val exp = remember(list) { list.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor } }
    val net = inc - exp
    val pInc = remember(prevList) { prevList.filter { it.type == "INCOME" }.sumOf { it.amountMinor } }
    val pExp = remember(prevList) { prevList.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor } }
    val rate = if (inc > 0) maxOf(0, (net.toDouble() / inc * 100).roundToInt()) else 0

    // Expense by category, rolled up to parents (web's parts).
    val parts = remember(list, catMap) {
        list.filter { it.type == "EXPENSE" }
            .groupBy { rollup(it.categoryId)?.id ?: -1L }
            .map { (cid, l) -> (rollup(cid) ?: catMap[cid]) to l.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
    }
    val maxPart = parts.firstOrNull()?.second ?: 1L
    val rest = parts.drop(5)
    val restVal = rest.sumOf { it.second }
    val big = remember(list) { list.filter { it.type == "EXPENSE" }.sortedByDescending { it.amountMinor }.take(3) }

    val daysInMonth = remember(year, month0) {
        Calendar.getInstance().apply { set(year, month0, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val isCurrentMonth = year == nowCal.get(Calendar.YEAR) && month0 == nowCal.get(Calendar.MONTH)
    val elapsed = if (isCurrentMonth) nowCal.get(Calendar.DAY_OF_MONTH) else daysInMonth

    val busyDay = remember(list) {
        list.filter { it.type == "EXPENSE" }
            .groupBy { Dates.startOfDay(it.timestamp) }
            .mapValues { (_, l) -> l.sumOf { it.amountMinor } }
            .maxByOrNull { it.value }
    }
    val monthTs = remember(year, month0) {
        Calendar.getInstance().apply { set(year, month0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
    val prevTs = Dates.addMonths(monthTs, -1)
    val prevShort = remember(prevTs) { Dates.monthShort(prevTs) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { TopBar("Laporan", badge = "BARU", onBack = onBack) }
        // Month navigation
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(
                    icon = Icons.Filled.ChevronLeft,
                    onClick = { ymKey -= 1 }, desc = "Bulan sebelumnya", iconSize = 17.dp
                )
                Text(
                    Dates.monthLabel(monthTs),
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.5.sp,
                    color = pal.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconBtn(
                    icon = Icons.Filled.ChevronRight,
                    onClick = { ymKey += 1 }, desc = "Bulan berikutnya", iconSize = 17.dp
                )
            }
        }
        // Stats 2x2
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Pemasukan", MoneyFormat.format(inc, currency), Modifier.weight(1f))
                    StatTile("Pengeluaran", MoneyFormat.format(exp, currency), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        "Bersih",
                        (if (net >= 0) "+" else "−") + MoneyFormat.format(kotlin.math.abs(net), currency),
                        Modifier.weight(1f),
                        valueColor = if (net >= 0) pal.successText else MaterialTheme.colorScheme.error
                    )
                    StatTile("Rasio tabungan", "$rate%", Modifier.weight(1f))
                }
            }
        }
        // Comparison vs previous month
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Dibanding ${Dates.monthLabel(prevTs)}")
                SaldoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    CmpRow("Pemasukan", inc, pInc, lowerGood = false, prevShort = prevShort, currency = currency)
                    HorizontalDivider(thickness = 1.dp, color = pal.line)
                    CmpRow("Pengeluaran", exp, pExp, lowerGood = true, prevShort = prevShort, currency = currency)
                }
            }
        }
        // Top categories
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle(
                    "Kategori teratas",
                    span = if (exp > 0) "dari total keluar ${MoneyFormat.formatCompact(exp, currency)}" else null
                )
                if (parts.isEmpty()) {
                    EmptyState("Tidak ada pengeluaran", "Belum ada pengeluaran tercatat di bulan ini.")
                } else {
                    SaldoCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        parts.take(5).forEach { (cat, v) ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        cat?.name ?: "Tanpa kategori",
                                        fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp, color = pal.ink, modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${if (exp > 0) (v * 100 / exp) else 0}%",
                                        fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                                        style = tnumStyle
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        MoneyFormat.format(v, currency),
                                        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp, color = pal.ink, style = tnumStyle
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    Modifier.fillMaxWidth().height(7.dp)
                                        .clip(RoundedCornerShape(99.dp)).background(pal.surface3)
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth((v.toDouble() / maxPart).toFloat().coerceIn(0f, 1f))
                                            .height(7.dp).clip(RoundedCornerShape(99.dp))
                                            .background(colorOf(cat?.colorHex ?: "#9E9E9E"))
                                    )
                                }
                            }
                        }
                        if (rest.isNotEmpty()) {
                            Text(
                                "+ ${rest.size} kategori lain · ${MoneyFormat.format(restVal, currency)}",
                                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted,
                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        // Biggest expenses
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Pengeluaran terbesar")
                if (big.isEmpty()) {
                    EmptyState("Tidak ada pengeluaran", "Belum ada pengeluaran tercatat di bulan ini.")
                } else {
                    DividerList(items = big, key = { it.id }, modifier = Modifier.fillMaxWidth()) { t ->
                        TxnRow(txn = t, catMap = catMap, accMap = accMap, currency = currency, onClick = {})
                    }
                }
            }
        }
        // Month facts
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Fakta bulan ini")
                SaldoCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        FactRow(
                            "Hari terboros",
                            busyDay?.let { "${Dates.dayMonth(it.key)} · ${MoneyFormat.format(it.value, currency)}" } ?: "—"
                        )
                        FactRow(
                            "Rata-rata keluar per hari",
                            MoneyFormat.format((exp + elapsed / 2) / maxOf(1, elapsed), currency)
                        )
                        FactRow("Total catatan", "${list.size} transaksi")
                        FactRow("Anggaran terpakai", "${budgets.size} kategori dipantau")
                    }
                }
            }
        }
        // Downloads
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(pal.accent)
                        .clickable {
                            vm.writeDownload(
                                "laporan-saldo-${fileYm(year, month0)}.txt", "text/plain",
                                buildReportText(
                                    year, month0, inc, exp, net, rate, pInc, pExp, prevShort,
                                    parts, list, catMap, currency
                                )
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Download, contentDescription = null,
                            tint = pal.accentInk, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unduh laporan (.txt)", fontFamily = Jakarta,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = pal.accentInk)
                    }
                }
                SaldoCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp),
                    onClick = {
                        vm.writeDownload(
                            "saldo-${fileYm(year, month0)}.csv", "text/csv",
                            buildMonthCsv(list, catMap, accMap)
                        )
                    }
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Ekspor transaksi bulan ini (CSV)", fontFamily = Jakarta,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = pal.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun CmpRow(label: String, cur: Long, prv: Long, lowerGood: Boolean, prevShort: String, currency: String) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp, color = pal.ink, modifier = Modifier.weight(1f))
        Text(MoneyFormat.format(cur, currency), fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp, color = pal.ink, style = tnumStyle)
        Spacer(Modifier.width(10.dp))
        if (prv == 0L) {
            Box(
                Modifier.clip(RoundedCornerShape(99.dp)).background(pal.surface3)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("bulan lalu ${MoneyFormat.formatCompact(prv, currency)}",
                    fontFamily = Jakarta, fontSize = 11.sp, color = pal.muted, style = tnumStyle)
            }
        } else {
            val pct = ((cur - prv).toDouble() / prv * 100).roundToInt()
            val up = pct >= 0
            val good = if (lowerGood) !up else up
            Box(
                Modifier.clip(RoundedCornerShape(99.dp))
                    .background(if (good) pal.successFill.copy(alpha = 0.13f) else pal.dangerSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "${if (up) "▲" else "▼"} ${kotlin.math.abs(pct)}% vs $prevShort",
                    fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    color = if (good) pal.successText else MaterialTheme.colorScheme.error, style = tnumStyle
                )
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    val pal = saldoColors
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
            modifier = Modifier.weight(1f))
        Text(value, fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            color = pal.ink, style = tnumStyle)
    }
}

private fun fileYm(year: Int, month0: Int): String =
    "$year-${(month0 + 1).toString().padStart(2, '0')}"

/** Plain-text monthly report — mirrors the web downloadReport() layout. */
private fun buildReportText(
    year: Int, month0: Int,
    inc: Long, exp: Long, net: Long, rate: Int,
    pInc: Long, pExp: Long, prevShort: String,
    parts: List<Pair<Category?, Long>>,
    list: List<Txn>,
    catMap: Map<Long, Category>,
    currency: String
): String {
    val monthTs = Calendar.getInstance()
        .apply { set(year, month0, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sb = StringBuilder()
    sb.append("LAPORAN KEUANGAN — SALDO\n")
    sb.append(Dates.monthLabel(monthTs)).append('\n')
    sb.append("==========================\n")
    sb.append("Pemasukan      : ${MoneyFormat.format(inc, currency)}\n")
    sb.append("Pengeluaran    : ${MoneyFormat.format(exp, currency)}\n")
    sb.append("Bersih         : ${MoneyFormat.format(net, currency)}\n")
    sb.append("Rasio tabungan : $rate%\n")
    sb.append("Bulan lalu ($prevShort) : masuk ${MoneyFormat.format(pInc, currency)} · keluar ${MoneyFormat.format(pExp, currency)}\n")
    sb.append('\n')
    sb.append("KATEGORI PENGELUARAN\n")
    if (parts.isEmpty()) sb.append("(tidak ada pengeluaran)\n")
    else parts.forEach { (cat, v) ->
        sb.append("- ${cat?.name ?: "Tanpa kategori"}: ${MoneyFormat.format(v, currency)} (${if (exp > 0) v * 100 / exp else 0}%)\n")
    }
    sb.append('\n')
    sb.append("TRANSAKSI BULAN INI (${list.size})\n")
    list.forEach { t ->
        val child = t.categoryId?.let { catMap[it] }
        val parent = child?.parentId?.let { catMap[it] }
        val catName = when {
            t.type == "TRANSFER" -> "Transfer"
            parent != null -> parent.name
            child != null -> child.name
            else -> "Transaksi"
        }
        val label = t.note.ifBlank { child?.takeIf { parent != null }?.name ?: "-" }
        val sign = when (t.type) { "INCOME" -> "+"; "EXPENSE" -> "-"; else -> "" }
        sb.append("${df.format(java.util.Date(t.timestamp))} | $catName | $label | $sign${MoneyFormat.format(t.amountMinor, currency)}\n")
    }
    sb.append('\n')
    sb.append("Dibuat dari aplikasi Saldo · ${Dates.fullDate(System.currentTimeMillis())}")
    return sb.toString()
}

/** Per-month CSV — mirrors the web exportMonthCSV() (semicolon-separated). */
private fun buildMonthCsv(
    list: List<Txn>,
    catMap: Map<Long, Category>,
    accMap: Map<Long, com.jiko.moneytracker.data.Account>
): String {
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sb = StringBuilder("Tanggal;Tipe;Kategori;Subkategori;Akun;Akun Tujuan;Catatan;Tag;Jumlah\n")
    list.forEach { t ->
        val child = t.categoryId?.let { catMap[it] }
        val parent = child?.parentId?.let { catMap[it] }
        val catName = parent?.name ?: child?.name ?: ""
        val subName = if (parent != null) child?.name ?: "" else ""
        val amount = if (t.type == "EXPENSE") -t.amountMinor else t.amountMinor
        sb.append(df.format(java.util.Date(t.timestamp))).append(';')
            .append(t.type).append(';')
            .append(catName).append(';')
            .append(subName).append(';')
            .append(accMap[t.accountId]?.name ?: "").append(';')
            .append(t.toAccountId?.let { accMap[it]?.name } ?: "").append(';')
            .append(t.note.replace(";", ",")).append(';')
            .append(t.tags.replace(";", ",")).append(';')
            .append(amount).append('\n')
    }
    return sb.toString()
}
