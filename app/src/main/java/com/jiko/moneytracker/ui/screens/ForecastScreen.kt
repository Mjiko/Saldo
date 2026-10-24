package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.Forecast
import com.jiko.moneytracker.util.MoneyFormat
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar

/** Prakiraan Saldo — balance projection, mirroring the web forecast screen. */
@Composable
fun ForecastScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val txns by vm.txns.collectAsState()
    val recurrings by vm.recurrings.collectAsState()
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors

    val balances by remember(accounts) {
        if (accounts.isEmpty()) flowOf(emptyMap<Long, Long>())
        else combine(accounts.map { vm.balanceOf(it.id) }) { arr ->
            accounts.mapIndexed { i, a -> a.id to arr[i] }.toMap()
        }
    }.collectAsState(initial = emptyMap())
    val totalBalance = remember(balances) { balances.values.sum() }

    val now = remember { System.currentTimeMillis() }
    val f = remember(txns, recurrings, totalBalance) {
        Forecast.compute(txns, recurrings, totalBalance, now)
    }
    val catMap = remember(categories) { categories.associateBy { it.id } }
    val paydayText = f.paydayTs?.let { Dates.dayMonth(it) } ?: "akhir bulan"
    val monthEndLabel = Dates.fullDate(f.monthEndTs)
    val endShort = Dates.dayMonth(f.horizonEndTs)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item { TopBar("Prakiraan Saldo", badge = "BARU", onBack = onBack) }
        item {
            Text(
                "Proyeksi dari jadwal berulang dan rata-rata belanja harianmu — berubah otomatis setiap ada transaksi atau jadwal baru.",
                fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
        // Hero: projected balance at end of this month
        item {
            SaldoCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(top = 14.dp)
            ) {
                Text(
                    "Perkiraan saldo · $monthEndLabel",
                    fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                )
                Text(
                    MoneyFormat.format(f.atMonthEnd, currency),
                    fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp, letterSpacing = (-0.6).sp, color = pal.ink,
                    style = tnumStyle, modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    buildString {
                        append("dari ${MoneyFormat.format(totalBalance, currency)} hari ini · ")
                        if (f.paydayTs != null) append("sudah termasuk gajian $paydayText dan ")
                        append("semua tagihan terjadwal")
                    },
                    fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                )
            }
        }
        // Stat tiles 2x2
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        "Saldo saat gajian · $paydayText",
                        MoneyFormat.format(f.atPayday, currency),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        "Perkiraan · $endShort",
                        MoneyFormat.format(f.atHorizonEnd, currency),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        "Rata-rata belanja harian",
                        MoneyFormat.format(f.avgDailySpend, currency),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        "Aman dibelanjakan / hari",
                        MoneyFormat.format(f.safeDaily, currency),
                        modifier = Modifier.weight(1f),
                        valueColor = pal.successText
                    )
                }
            }
        }
        // Projection chart
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Proyeksi 6 minggu", span = "garis saldo harian")
                SaldoCard(Modifier.fillMaxWidth()) {
                    ForecastChart(
                        points = f.points.map { it.dayStartTs to it.balance },
                        paydayTs = f.paydayTs,
                        currency = currency
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        val labels = chartLabels(f.points.map { it.dayStartTs }, f.monthEndTs)
                        labels.forEachIndexed { i, s ->
                            Text(
                                s, fontFamily = Jakarta, fontSize = 11.sp, color = pal.faint,
                                style = tnumStyle,
                                modifier = Modifier.weight(1f),
                                textAlign = when (i) {
                                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                                    labels.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                                    else -> androidx.compose.ui.text.style.TextAlign.Center
                                }
                            )
                        }
                    }
                }
            }
        }
        // Upcoming schedule
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Jadwal mendatang", span = "${f.events.size} kejadian")
                if (f.events.isEmpty()) {
                    EmptyState(
                        "Belum ada jadwal",
                        "Tambahkan transaksi berulang — misalnya gaji atau sewa — agar proyeksi lebih akurat."
                    )
                } else {
                    DividerList(
                        items = f.events.take(6),
                        key = { "${it.dayStartTs}-${it.recurring.id}" },
                        modifier = Modifier.fillMaxWidth()
                    ) { e ->
                        val r = e.recurring
                        val cat = r.categoryId?.let { catMap[it] }
                        val inc = r.type == "INCOME"
                        ScheduleRow(
                            iconKey = cat?.iconKey ?: "autorenew",
                            colorHex = cat?.colorHex ?: "#9E9E9E",
                            name = r.note.ifBlank { cat?.name ?: "Jadwal" },
                            sub = "${Dates.dayLabel(e.dayStartTs)} · ${cadenceLabel(r.cadence)}",
                            amountText = (if (inc) "+" else "−") + MoneyFormat.format(r.amountMinor, currency),
                            amountColor = if (inc) pal.successText else pal.ink,
                            balanceText = "saldo ≈ ${MoneyFormat.formatCompact(e.balanceAfter, currency)}"
                        )
                    }
                }
            }
        }
        // How it works
        item {
            SaldoCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text("Cara menghitung", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp, color = pal.ink)
                Spacer(Modifier.height(4.dp))
                val todayCal = Calendar.getInstance().apply { timeInMillis = now }
                Text(
                    "Saldo hari ini, ditambah jadwal berulang (gaji, sewa, langganan), lalu dikurangi rata-rata belanja harian di luar kategori terjadwal — bulan ini ${MoneyFormat.format(f.avgDailySpend, currency)}/hari dari data 1–${todayCal.get(Calendar.DAY_OF_MONTH)} ${Dates.monthName(now)}. “Aman dibelanjakan” adalah sisa saldo setelah tagihan sebelum gajian, dibagi ${f.daysToPayday} hari menuju gajian.",
                    fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    iconKey: String,
    colorHex: String,
    name: String,
    sub: String,
    amountText: String,
    amountColor: androidx.compose.ui.graphics.Color,
    balanceText: String
) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        CategoryIcon(iconKey, colorHex, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = pal.ink, maxLines = 1)
            Text(sub, fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted, maxLines = 1)
        }
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(amountText, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = amountColor, style = tnumStyle)
            Text(balanceText, fontFamily = Jakarta, fontSize = 11.sp, color = pal.faint,
                style = tnumStyle, maxLines = 1)
        }
    }
}

private fun cadenceLabel(cadence: String): String = when (cadence) {
    "DAILY" -> "Harian"
    "WEEKLY" -> "Mingguan"
    "YEARLY" -> "Tahunan"
    else -> "Bulanan"
}

/** Axis labels under the chart: today / end of this month / mid-next / horizon end. */
private fun chartLabels(days: List<Long>, monthEndTs: Long): List<String> {
    if (days.isEmpty()) return listOf("", "", "", "")
    val mid = days.getOrNull(days.size / 2) ?: days.last()
    return listOf(
        Dates.dayMonth(days.first()),
        Dates.dayMonth(monthEndTs),
        Dates.dayMonth(mid),
        Dates.dayMonth(days.last())
    )
}

/** Accent polyline of daily projected balances with a payday marker (web SVG chart). */
@Composable
private fun ForecastChart(
    points: List<Pair<Long, Long>>,
    paydayTs: Long?,
    currency: String
) {
    val pal = saldoColors
    val density = LocalDensity.current
    val labelSizePx = with(density) { 9.5.sp.toPx() }
    val maxLabelSizePx = with(density) { 9.sp.toPx() }
    val paydayLabel = paydayTs?.let { "Gajian ${Dates.dayMonth(it)}" } ?: ""
    val maxValText = remember(points) {
        MoneyFormat.formatCompact(points.maxOfOrNull { it.second } ?: 0L, currency)
    }
    val ringColor = MaterialTheme.colorScheme.surface
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val padT = 14.dp.toPx()
        val padB = 8.dp.toPx()
        val vals = points.map { it.second }
        var mn = vals.min().toDouble()
        var mx = vals.max().toDouble()
        val span = (mx - mn).takeIf { it != 0.0 } ?: 1.0
        mn -= span * 0.1
        mx += span * 0.16
        fun x(i: Int) = 6.dp.toPx() + i * (w - 12.dp.toPx()) / (points.size - 1)
        fun y(v: Long) = (padT + (mx - v) / (mx - mn) * (h - padT - padB)).toFloat()

        val path = Path()
        points.forEachIndexed { i, p ->
            val px = x(i); val py = y(p.second)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color = pal.accent, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Max label (top-left, web style)
        drawContext.canvas.nativeCanvas.drawText(
            maxValText, 6.dp.toPx(), 10.dp.toPx(),
            android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = maxLabelSizePx
                color = pal.faint.toArgb()
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }
        )

        // Payday marker
        val payIdx = paydayTs?.let { ts -> points.indexOfFirst { it.first == ts } } ?: -1
        if (payIdx >= 0) {
            val cx = x(payIdx); val cy = y(points[payIdx].second)
            drawCircle(color = pal.successFill, radius = 4.2.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = ringColor, radius = 4.2.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            val tx = minOf(cx + 8.dp.toPx(), w - 86.dp.toPx()).coerceAtLeast(0f)
            val ty = (cy - 8.dp.toPx()).coerceAtLeast(labelSizePx)
            drawContext.canvas.nativeCanvas.drawText(
                paydayLabel, tx, ty,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = labelSizePx
                    color = pal.successText.toArgb()
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                }
            )
        }
        // End marker
        val lastIdx = points.lastIndex
        drawCircle(color = pal.accent, radius = 4.2.dp.toPx(), center = Offset(x(lastIdx), y(points[lastIdx].second)))
        drawCircle(color = ringColor, radius = 4.2.dp.toPx(), center = Offset(x(lastIdx), y(points[lastIdx].second)), style = Stroke(width = 2.dp.toPx()))
    }
}
