package com.jiko.moneytracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.MoneyFormat
import kotlin.math.atan2

data class DonutSegment(val label: String, val value: Long, val color: Color)

/** Ring progress (web ringSVG): surface3 track, round-cap arc from -90°. */
@Composable
fun ProgressRing(
    pct: Float,
    color: Color,
    size: Dp,
    stroke: Dp,
    center: @Composable BoxScope.() -> Unit = {}
) {
    val pal = saldoColors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val pad = sw / 2 + 1.dp.toPx()
            drawArc(
                color = pal.surface3,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(pad, pad),
                size = Size(size.toPx() - pad * 2, size.toPx() - pad * 2),
                style = Stroke(width = sw)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * pct.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(pad, pad),
                size = Size(size.toPx() - pad * 2, size.toPx() - pad * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        center()
    }
}

/** Donut: 148dp default, 15dp stroke, round caps, small gaps between segments. */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    selectedIndex: Int = -1,
    onSelect: (Int) -> Unit = {}
) {
    val pal = saldoColors
    val total = remember(segments) { segments.sumOf { it.value } }
    val angles = remember(segments, total) {
        if (total <= 0) emptyList()
        else {
            var acc = -90f
            segments.map { s ->
                val sweep = (s.value.toFloat() / total * 360f)
                val a = acc to sweep
                acc += sweep
                a
            }
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(angles) {
                    detectTapGestures { tap ->
                        if (angles.isEmpty()) return@detectTapGestures
                        val c = Offset(size.toPx() / 2, size.toPx() / 2)
                        val dx = tap.x - c.x
                        val dy = tap.y - c.y
                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (deg < -90) deg += 360
                        val idx = angles.indexOfFirst { (start, sweep) ->
                            val end = start + sweep
                            deg >= start && deg < end
                        }
                        if (idx >= 0) onSelect(idx)
                    }
                }
        ) {
            if (angles.isEmpty()) {
                drawCircle(
                    color = pal.surface3,
                    style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
                )
                return@Canvas
            }
            val stroke = 15.dp.toPx()
            val pad = stroke / 2 + 4.dp.toPx()
            angles.forEachIndexed { i, (start, sweep) ->
                drawArc(
                    color = segments[i].color,
                    startAngle = start,
                    sweepAngle = (sweep - 2.5f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(size.toPx() - pad * 2, size.toPx() - pad * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    alpha = if (selectedIndex == -1 || selectedIndex == i) 1f else 0.35f
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                fontSize = 11.sp, color = pal.muted)
            Text(centerBottom, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                fontSize = 16.5.sp, color = pal.ink, textAlign = TextAlign.Center,
                style = tnumStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Donut on top, legend below full width (web `.donutwrap` stack). */
@Composable
fun DonutCard(
    segments: List<DonutSegment>,
    currency: String,
    centerLabel: String,
    centerValue: String,
    selectedIndex: Int = -1,
    onDrill: (Int) -> Unit
) {
    val pal = saldoColors
    val total = remember(segments) { segments.sumOf { it.value } }
    SaldoCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DonutChart(
                segments = segments,
                centerTop = centerLabel,
                centerBottom = centerValue,
                selectedIndex = selectedIndex,
                onSelect = onDrill
            )
            Spacer(Modifier.height(8.dp))
            segments.take(5).forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDrill(i) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(9.dp).clip(RoundedCornerShape(3.dp))
                            .background(s.color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, color = pal.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${if (total > 0) (s.value * 100 / total).toInt() else 0}%",
                        fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End, style = tnumStyle
                    )
                    Text(
                        MoneyFormat.formatCompact(s.value, currency),
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp, color = pal.ink,
                        modifier = Modifier.width(76.dp),
                        textAlign = TextAlign.End, style = tnumStyle,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Grouped income-vs-expense bars with rounded caps, memoized. */
@Composable
fun GroupedBarChart(
    months: List<String>,
    income: List<Long>,
    expense: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    incomeColor: Color = Color(0xFF00C853),
    expenseColor: Color = Color(0xFFFF5722),
) {
    val maxV = remember(income, expense) {
        (income + expense).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val n = months.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val barW = (slot * 0.22f).coerceAtMost(26.dp.toPx())
        val baseY = size.height - 22.dp.toPx()
        val h = size.height - 30.dp.toPx()
        months.forEachIndexed { i, _ ->
            val cx = slot * i + slot / 2
            val ih = (income.getOrElse(i) { 0L }.toFloat() / maxV * h).coerceAtLeast(3.dp.toPx())
            val eh = (expense.getOrElse(i) { 0L }.toFloat() / maxV * h).coerceAtLeast(3.dp.toPx())
            drawRoundRect(incomeColor, Offset(cx - barW - 3.dp.toPx(), baseY - ih),
                Size(barW, ih), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
            drawRoundRect(expenseColor, Offset(cx + 3.dp.toPx(), baseY - eh),
                Size(barW, eh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
        }
    }
}

/** Simple net-balance line chart, memoized. */
@Composable
fun LineChart(
    points: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    lineColor: Color = Color(0xFFFF5722),
) {
    val path = remember(points) { points } // drawing below is cheap; memo on data
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (path.size < 2) return@Canvas
        val max = (path.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val min = (path.minOrNull() ?: 0L).coerceAtMost(0L).toFloat()
        val range = (max - min).takeIf { it > 0 } ?: 1f
        val w = size.width
        val h = size.height - 16.dp.toPx()
        val pts = path.mapIndexed { i, v ->
            val x = if (path.size == 1) w / 2 else w * i / (path.size - 1)
            val y = h - ((v - min) / range * h)
            Offset(x.toFloat(), y.toFloat())
        }
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        drawPath(p, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.forEach { drawCircle(lineColor, 3.dp.toPx(), it) }
    }
}
