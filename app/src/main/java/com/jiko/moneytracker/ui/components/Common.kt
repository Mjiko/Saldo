package com.jiko.moneytracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.data.Account
import com.jiko.moneytracker.data.Budget
import com.jiko.moneytracker.data.Category
import com.jiko.moneytracker.data.Txn
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat

// ---------- Shared text style ----------

/** Tabular figures for all money text (web `.num`). */
val tnumStyle = TextStyle(fontFeatureSettings = "tnum")

// ---------- SaldoCard ----------

@Composable
fun SaldoCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick ?: {}
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

// ---------- IconBtn ----------

/** 38dp icon button: surface bg + soft shadow, muted icon. */
@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    desc: String? = null,
    iconSize: Dp = 19.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = saldoColors.muted,
            modifier = Modifier.size(iconSize))
    }
}

// ---------- TopBar ----------

@Composable
fun TopBar(
    title: String,
    date: String? = null,
    sub: String? = null,
    badge: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconBtn(Icons.Filled.ArrowBack, onClick = onBack, desc = "Kembali")
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            if (date != null) {
                Text(date, fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp, color = pal.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp, letterSpacing = (-0.42).sp, color = pal.ink)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(pal.accentSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(badge, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp, color = pal.accent)
                    }
                }
            }
        }
        if (sub != null) {
            Text(sub, fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp, color = pal.muted)
            Spacer(Modifier.width(4.dp))
        }
        trailing()
    }
}

// ---------- SectionTitle ----------

@Composable
fun SectionTitle(
    text: String,
    span: String? = null,
    linkText: String? = null,
    onLink: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pal = saldoColors
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, letterSpacing = (-0.15).sp, color = pal.ink)
        if (span != null) {
            Spacer(Modifier.width(8.dp))
            Text(span, fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted)
        }
        if (linkText != null) {
            Spacer(Modifier.weight(1f))
            Text(linkText, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp, color = pal.accent,
                modifier = Modifier.clickable { onLink?.invoke() })
        }
    }
}

// ---------- CategoryIcon ----------

/** Solid-color rounded square with white icon (web `.cic` style). */
@Composable
fun CategoryIcon(iconKey: String, colorHex: String, size: Int = 40, iconSize: Int = 0) {
    val bg = colorOf(colorHex)
    val ic = if (iconSize > 0) iconSize else (size * 0.475f).toInt()
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.325f).dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(iconFor(iconKey), contentDescription = null,
            tint = Color.White, modifier = Modifier.size(ic.dp))
    }
}

// ---------- TxnRow (web layout) ----------

/** Web-style transaction row. Tap = open, long-press = batch select. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxnRow(
    txn: Txn,
    catMap: Map<Long, Category>,
    accMap: Map<Long, Account>,
    currency: String,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
) {
    val pal = saldoColors
    val child = txn.categoryId?.let { catMap[it] }
    val parent = child?.parentId?.let { catMap[it] }
    val (iconKey, colorHex, title) = when {
        txn.type == "TRANSFER" -> Triple("swap", "#5A6472", "Transfer")
        child != null && parent != null -> Triple(parent.iconKey, parent.colorHex, parent.name)
        child != null -> Triple(child.iconKey, child.colorHex, child.name)
        else -> Triple("more", "#9E9E9E", "Transaksi")
    }
    val sub = buildList {
        if (parent != null && child != null) add(child.name)
        if (txn.note.isNotBlank()) add(txn.note)
    }.joinToString(" · ").ifBlank { Dates.dayShort(txn.timestamp) }
    val accLine = if (txn.type == "TRANSFER") {
        val from = accMap[txn.accountId]?.name ?: ""
        val to = txn.toAccountId?.let { accMap[it]?.name } ?: ""
        "$from → $to"
    } else {
        accMap[txn.accountId]?.name ?: ""
    }
    val (prefix, amtColor) = when (txn.type) {
        "INCOME" -> "+" to pal.successText
        "EXPENSE" -> "−" to pal.ink
        else -> "" to pal.ink
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onClick() },
                onLongClick = onToggleSelect
            )
            .background(
                if (selected) pal.accent.copy(alpha = 0.08f) else Color.Transparent
            )
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() },
                modifier = Modifier.padding(end = 4.dp))
        }
        CategoryIcon(iconKey, colorHex, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = pal.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$prefix${MoneyFormat.format(txn.amountMinor, currency)}",
                fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = amtColor, style = tnumStyle
            )
            Text(accLine, fontFamily = Jakarta, fontSize = 11.sp, color = pal.faint,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- DividerList ----------

/** Card-style list with 1dp hairline dividers between items. */
@Composable
fun <T> DividerList(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit
) {
    val pal = saldoColors
    SaldoCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    ) {
        items.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = pal.line)
            key(key(item)) { row(item) }
        }
    }
}

// ---------- BudgetRow ----------

@Composable
fun BudgetRow(
    budget: Budget,
    cat: Category?,
    spent: Long,
    currency: String,
    onClick: () -> Unit
) {
    val pal = saldoColors
    val pct = if (budget.amountMinor > 0) spent.toDouble() / budget.amountMinor else 0.0
    val col = when {
        pct >= 1 -> MaterialTheme.colorScheme.error
        pct >= 0.9 -> pal.warningText
        pct >= 0.75 -> pal.warningFill
        else -> cat?.let { colorOf(it.colorHex) } ?: pal.successFill
    }
    val status = if (pct >= 1) "Lewat ${MoneyFormat.format(spent - budget.amountMinor, currency)}"
    else "Sisa ${MoneyFormat.format(budget.amountMinor - spent, currency)}"
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(cat?.iconKey ?: "more", cat?.colorHex ?: "#9E9E9E", size = 36)
            Spacer(Modifier.width(10.dp))
            Text(cat?.name ?: "Semua pengeluaran", fontFamily = Jakarta,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = pal.ink,
                modifier = Modifier.weight(1f))
            Text("${(pct * 100).toInt()}%", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = col, style = tnumStyle)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${MoneyFormat.format(spent, currency)} dari ${MoneyFormat.format(budget.amountMinor, currency)}",
                fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                style = tnumStyle
            )
            Text(" · ", fontSize = 12.sp, color = pal.muted)
            Text(status, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = col)
        }
        Spacer(Modifier.height(9.dp))
        LinearProgressIndicator(
            progress = { pct.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(99.dp)),
            color = col,
            trackColor = pal.surface3,
        )
    }
}

// ---------- MenuRow ----------

@Composable
fun MenuRow(
    icon: ImageVector,
    iconBg: Color,
    name: String,
    desc: String,
    badge: String? = null,
    right: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = pal.ink)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(pal.accentSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badge, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp, color = pal.accent)
                    }
                }
            }
            if (desc.isNotEmpty()) {
                Text(desc, fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                    fontSize = 12.sp, color = pal.muted)
            }
        }
        right?.invoke()
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint = pal.faint, modifier = Modifier.size(20.dp))
    }
}

// ---------- SegControl / TypeSeg ----------

@Composable
fun SegControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = saldoColors
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f)
                    .then(if (sel) Modifier.shadow(2.dp, RoundedCornerShape(9.dp)) else Modifier)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(o, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp, color = if (sel) pal.ink else pal.muted)
            }
        }
    }
}

/** Type picker for the add sheet: Keluar / Masuk / Transfer. */
@Composable
fun TypeSeg(type: String, onType: (String) -> Unit, modifier: Modifier = Modifier) {
    val pal = saldoColors
    val options = listOf(
        "EXPENSE" to "Keluar",
        "INCOME" to "Masuk",
        "TRANSFER" to "Transfer"
    )
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (v, label) ->
            val sel = type == v
            val textColor = when {
                !sel -> pal.muted
                v == "EXPENSE" -> pal.accent
                v == "INCOME" -> pal.successText
                else -> pal.ink
            }
            Box(
                Modifier.weight(1f)
                    .then(if (sel) Modifier.shadow(2.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onType(v) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, color = textColor)
            }
        }
    }
}

// ---------- SaldoFilterChip ----------

/** Pill chip: surface + shadow; ON = ink bg + bg-colored text. */
@Composable
fun SaldoFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = saldoColors
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(99.dp))
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) pal.ink else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = if (selected) MaterialTheme.colorScheme.background else pal.muted)
    }
}

// ---------- SelectorChip (FlowRow-safe) ----------

/**
 * Selectable chip with a leading icon, built for FlowRow layouts: fixed 40dp
 * height and a single-line ellipsized label. A FlowRow measures each chip at
 * its intrinsic width (capped at the row width), so a chip can never be
 * squeezed into a mid-word wrap or stretched by its neighbours — the failure
 * mode seen on the Hasil pindaian screen where fixed Rows compressed chips.
 */
@Composable
fun SelectorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(40.dp),
        label = {
            Text(
                label, fontFamily = Jakarta, fontSize = 12.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = leadingIcon
    )
}

// ---------- SearchBar ----------

@Composable
fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Cari catatan, kategori, tag…"
) {
    val pal = saldoColors
    Row(
        modifier = modifier.fillMaxWidth()
            .height(44.dp)
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = pal.faint,
            modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = Jakarta, fontSize = 14.sp, color = pal.ink
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(placeholder, fontFamily = Jakarta, fontSize = 14.sp, color = pal.faint)
                }
                inner()
            }
        )
    }
}

// ---------- StatTile (web .fcstat) ----------

/** Label-over-value stat tile used by Prakiraan & Laporan. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    val pal = saldoColors
    SaldoCard(modifier = modifier, contentPadding = PaddingValues(13.dp)) {
        Text(label, fontFamily = Jakarta, fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp, color = pal.muted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
            fontSize = 16.5.sp, letterSpacing = (-0.33).sp,
            color = valueColor ?: pal.ink, style = tnumStyle, maxLines = 1)
    }
}

// ---------- EmptyState ----------

@Composable
fun EmptyState(
    title: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    val pal = saldoColors
    SaldoCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Wallet, contentDescription = null,
                    tint = pal.faint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp, color = pal.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(sub, fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                textAlign = TextAlign.Center)
        }
    }
}

// ---------- SaldoSwitch ----------

/** 46x27 pill: track surface3 off / successFill on, white 21dp knob. */
@Composable
fun SaldoSwitch(checked: Boolean, onChecked: (Boolean) -> Unit) {
    val pal = saldoColors
    Box(
        Modifier.size(46.dp, 27.dp)
            .clip(CircleShape)
            .background(if (checked) pal.successFill else pal.surface3)
            .clickable { onChecked(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier.size(21.dp).shadow(1.dp, CircleShape)
                .clip(CircleShape).background(Color.White)
        )
    }
}

/** Legacy tinted progress bar (used by category drill rows). */
@Composable
fun ProgressBar(value: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { value.coerceIn(0f, 1f) },
        modifier = modifier.height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = saldoColors.surface3,
    )
}

