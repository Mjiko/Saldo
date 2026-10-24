package com.jiko.moneytracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Debt
import com.jiko.moneytracker.data.DebtPayment
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import java.util.Calendar

/** Avatar palette from the web version; the color is hashed from the person's name. */
private val AVA_COLORS = listOf(
    "#FF5722", "#7C4DFF", "#00B0FF", "#00BFA5",
    "#D6339E", "#D9A404", "#3D5AFE", "#FF9100"
)

private fun paidOf(d: Debt, payments: List<DebtPayment>): Long =
    payments.filter { it.debtId == d.id }.sumOf { it.amountMinor }

private fun remainOf(d: Debt, payments: List<DebtPayment>): Long =
    maxOf(0L, d.amountMinor - paidOf(d, payments))

/** "lunas" | "sebagian" | "belum" — derived from payments, never stored. */
private fun statusOf(d: Debt, payments: List<DebtPayment>): String = when {
    remainOf(d, payments) <= 0 -> "lunas"
    paidOf(d, payments) > 0 -> "sebagian"
    else -> "belum"
}

private fun isOverdue(d: Debt, payments: List<DebtPayment>, now: Long): Boolean =
    d.due != null && Dates.startOfDay(d.due) < Dates.startOfDay(now) && remainOf(d, payments) > 0

/** Hutang & Piutang — debts & receivables, mirroring the web "debts" screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val debts by vm.debts.collectAsState()
    val payments by vm.debtPayments.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    val now = remember { System.currentTimeMillis() }

    var tab by remember { mutableStateOf("PIUTANG") }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    var sheet by remember { mutableStateOf("none") } // none | detail | pay | form

    val selected = remember(debts, selectedId) { debts.firstOrNull { it.id == selectedId } }

    val totalPiutang = remember(debts, payments) {
        debts.filter { it.side == "PIUTANG" }.sumOf { remainOf(it, payments) }
    }
    val totalHutang = remember(debts, payments) {
        debts.filter { it.side == "HUTANG" }.sumOf { remainOf(it, payments) }
    }
    val countOf = { side: String ->
        debts.count { it.side == side && statusOf(it, payments) != "lunas" }
    }

    val sideList = remember(debts, tab) { debts.filter { it.side == tab } }
    val active = remember(sideList, payments) {
        sideList.filter { statusOf(it, payments) != "lunas" }.sortedWith(
            compareBy<Debt> { if (isOverdue(it, payments, now)) 0 else 1 }
                .thenBy { it.due ?: Long.MAX_VALUE }
                .thenByDescending { it.date }
        )
    }
    val done = remember(sideList, payments) {
        sideList.filter { statusOf(it, payments) == "lunas" }.sortedByDescending { it.date }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            TopBar(
                title = "Hutang & Piutang",
                badge = "BARU",
                onBack = onBack,
                trailing = {
                    IconBtn(Icons.Filled.Add, onClick = {
                        editing = null; sheet = "form"
                    }, desc = "Tambah catatan")
                }
            )
        }
        item {
            Text(
                "Catat uangmu yang dipinjam orang lain dan hutangmu ke orang lain — cicilan tercatat, status berubah otomatis sampai lunas.",
                fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        // Live totals across both sides
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    "Piutang · uang di orang lain",
                    MoneyFormat.format(totalPiutang, currency),
                    modifier = Modifier.weight(1f),
                    valueColor = pal.successText
                )
                StatTile(
                    "Hutang · kamu berutang",
                    MoneyFormat.format(totalHutang, currency),
                    modifier = Modifier.weight(1f),
                    valueColor = pal.accent
                )
            }
        }
        item {
            SegControl(
                options = listOf("Piutang (${countOf("PIUTANG")})", "Hutang (${countOf("HUTANG")})"),
                selected = if (tab == "PIUTANG") 0 else 1,
                onSelect = { tab = if (it == 0) "PIUTANG" else "HUTANG" },
                modifier = Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)
            )
        }
        if (active.isNotEmpty()) {
            item {
                DividerList(
                    items = active,
                    key = { it.id },
                    modifier = Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)
                ) { d ->
                    DebtRow(d, payments, currency, now) {
                        selectedId = d.id; sheet = "detail"
                    }
                }
            }
        }
        if (done.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                    SectionTitle("Selesai", span = "${done.size} lunas")
                    DividerList(
                        items = done,
                        key = { it.id },
                        modifier = Modifier.fillMaxWidth()
                    ) { d ->
                        DebtRow(d, payments, currency, now) {
                            selectedId = d.id; sheet = "detail"
                        }
                    }
                }
            }
        }
        if (sideList.isEmpty()) {
            item {
                EmptyState(
                    title = if (tab == "PIUTANG") "Belum ada piutang" else "Belum ada hutang",
                    sub = if (tab == "PIUTANG")
                        "Ketuk + untuk mencatat uang yang kamu pinjamkan."
                    else "Ketuk + untuk mencatat hutangmu ke orang lain.",
                    modifier = Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)
                )
            }
        } else if (active.isEmpty()) {
            item {
                Text(
                    "Semua catatan di sisi ini sudah lunas.",
                    fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(top = 14.dp)
                )
            }
        }
    }

    when (sheet) {
        "detail" -> selected?.let { d ->
            DebtDetailSheet(
                vm = vm, debt = d, payments = payments, currency = currency,
                now = now,
                onClose = { sheet = "none" },
                onPay = { sheet = "pay" },
                onEdit = { editing = d; sheet = "form" }
            )
        }
        "pay" -> selected?.let { d ->
            DebtPaySheet(
                vm = vm, debt = d,
                remain = remainOf(d, payments),
                currency = currency,
                onClose = { sheet = "none" }
            )
        }
        "form" -> DebtFormSheet(
            vm = vm, editing = editing, defaultSide = tab, currency = currency,
            onClose = { sheet = "none" },
            onSaved = { side -> tab = side; sheet = "none" }
        )
    }
}

// ---------- list row ----------

@Composable
private fun DebtRow(
    d: Debt,
    payments: List<DebtPayment>,
    currency: String,
    now: Long,
    onClick: () -> Unit
) {
    val pal = saldoColors
    val done = statusOf(d, payments) == "lunas"
    val meta = buildString {
        append("Pinjam ${Dates.dayLabel(d.date)}")
        d.due?.let { append(" · Tempo ${Dates.dayLabel(it)}") }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 11.dp)
            .then(if (done) Modifier.alpha(0.55f) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DebtAvatar(d.person, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.person, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = pal.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(d.note.ifBlank { "—" }, fontFamily = Jakarta, fontSize = 12.sp,
                color = pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, fontFamily = Jakarta, fontSize = 11.5.sp, color = pal.faint,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DebtStatusChip(statusOf(d, payments))
                if (isOverdue(d, payments, now)) DebtChip("Lewat jatuh tempo", pal.dangerSoft, MaterialTheme.colorScheme.error)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (done) {
                Text(
                    MoneyFormat.format(d.amountMinor, currency),
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = pal.ink, style = tnumStyle,
                    textDecoration = TextDecoration.LineThrough
                )
                Text("Sudah lunas", fontFamily = Jakarta, fontSize = 11.sp, color = pal.faint)
            } else {
                Text(
                    MoneyFormat.format(remainOf(d, payments), currency),
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.5.sp,
                    color = pal.ink, style = tnumStyle
                )
                Text(
                    "dari ${MoneyFormat.format(d.amountMinor, currency)}",
                    fontFamily = Jakarta, fontSize = 11.sp, color = pal.faint, style = tnumStyle
                )
            }
        }
    }
}

@Composable
private fun DebtAvatar(name: String, size: Int) {
    val trimmed = name.trim()
    val hash = trimmed.sumOf { it.code }
    val bg = colorOf(AVA_COLORS[((hash % AVA_COLORS.size) + AVA_COLORS.size) % AVA_COLORS.size])
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (trimmed.firstOrNull() ?: '?').uppercase(),
            fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = (size * 0.38f).sp, color = Color.White
        )
    }
}

/** Small pill chip (web `.dchip`). */
@Composable
private fun DebtChip(label: String, bg: Color, fg: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 2.5.dp)
    ) {
        Text(label, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp, color = fg, maxLines = 1)
    }
}

@Composable
private fun DebtStatusChip(status: String) {
    val pal = saldoColors
    when (status) {
        "lunas" -> DebtChip("Lunas", pal.successFill.copy(alpha = 0.13f), pal.successText)
        "sebagian" -> DebtChip("Sebagian", pal.warningFill.copy(alpha = 0.15f), pal.warningText)
        else -> DebtChip("Belum lunas", MaterialTheme.colorScheme.surfaceVariant, pal.muted)
    }
}

// ---------- shared sheet chrome (mirrors MainActivity's sheets) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtSheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val pal = saldoColors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(38.dp, 4.5.dp)
                        .clip(RoundedCornerShape(2.25.dp))
                        .background(pal.surface3)
                )
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            content = content
        )
    }
}

@Composable
private fun SheetButton(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth().height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp, color = fg)
    }
}

// ---------- detail sheet ----------

@Composable
private fun DebtDetailSheet(
    vm: MoneyViewModel,
    debt: Debt,
    payments: List<DebtPayment>,
    currency: String,
    now: Long,
    onClose: () -> Unit,
    onPay: () -> Unit,
    onEdit: () -> Unit
) {
    val pal = saldoColors
    var confirmDelete by remember { mutableStateOf(false) }
    val mine = remember(payments, debt.id) { payments.filter { it.debtId == debt.id } }
    val paid = paidOf(debt, payments)
    val remain = remainOf(debt, payments)
    val status = statusOf(debt, payments)
    val pct = if (debt.amountMinor > 0) paid.toFloat() / debt.amountMinor else 0f

    DebtSheet(onClose = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DebtAvatar(debt.person, 48)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(debt.person, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 16.5.sp, color = pal.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (debt.side == "PIUTANG") "Piutang — uangmu di dia" else "Hutang — kamu berutang ke dia",
                    fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                DebtStatusChip(status)
                if (isOverdue(debt, payments, now))
                    DebtChip("Lewat jatuh tempo", pal.dangerSoft, MaterialTheme.colorScheme.error)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sisa ${if (debt.side == "PIUTANG") "piutang" else "hutang"}",
                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
            )
            Text(
                MoneyFormat.format(remain, currency),
                fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp, letterSpacing = (-0.64).sp, color = pal.ink,
                style = tnumStyle
            )
            Text(
                "dari total ${MoneyFormat.format(debt.amountMinor, currency)} · sudah " +
                    (if (debt.side == "PIUTANG") "dibayar " else "kamu bayar ") +
                    MoneyFormat.format(paid, currency),
                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted, style = tnumStyle
            )
        }
        ProgressBar(pct, pal.successFill, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        InfoRow("Tanggal pinjam", Dates.dayShort(debt.date))
        InfoRow("Jatuh tempo", debt.due?.let { Dates.dayShort(it) } ?: "—")
        InfoRow("Catatan", debt.note.ifBlank { "—" })

        SectionTitle("Riwayat pembayaran", span = "${mine.size} cicilan")
        if (mine.isEmpty()) {
            Text("Belum ada pembayaran tercatat.", fontFamily = Jakarta,
                fontSize = 13.sp, color = pal.muted)
        } else {
            mine.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                            .background(pal.successFill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Cicilan ke-${i + 1}", fontFamily = Jakarta,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = pal.ink)
                        Text(Dates.dayShort(p.date), fontFamily = Jakarta,
                            fontSize = 12.sp, color = pal.muted)
                    }
                    Text(
                        MoneyFormat.format(p.amountMinor, currency),
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = pal.successText, style = tnumStyle
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (status != "lunas") {
            SheetButton("Catat pembayaran", pal.accent, pal.accentInk) { onPay() }
            Spacer(Modifier.height(10.dp))
            SheetButton(
                "Tandai lunas",
                pal.successFill.copy(alpha = 0.13f), pal.successText
            ) { vm.settleDebt(debt); onClose() }
            Spacer(Modifier.height(10.dp))
        }
        SheetButton(
            "Edit", MaterialTheme.colorScheme.surface, pal.ink
        ) { onEdit() }
        Spacer(Modifier.height(10.dp))
        SheetButton(
            "Hapus catatan", pal.dangerSoft, MaterialTheme.colorScheme.error
        ) { confirmDelete = true }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus catatan?") },
            text = { Text("Catatan ${debt.person} beserta riwayat pembayarannya akan dihapus.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteDebt(debt); onClose() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted)
        Text(value, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, color = pal.ink, textAlign = TextAlign.End)
    }
    HorizontalDivider(thickness = 1.dp, color = pal.line)
}

// ---------- pay sheet ----------

@Composable
private fun DebtPaySheet(
    vm: MoneyViewModel,
    debt: Debt,
    remain: Long,
    currency: String,
    onClose: () -> Unit
) {
    val pal = saldoColors
    val context = LocalContext.current
    var amountText by remember(debt.id, remain) { mutableStateOf(remain.toString()) }
    var date by remember { mutableStateOf(Dates.startOfDay(System.currentTimeMillis())) }

    DebtSheet(onClose = onClose) {
        Text("Catat pembayaran", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp, color = pal.ink)
        Text(
            "${debt.person} · sisa ${MoneyFormat.format(remain, currency)}",
            fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() }.take(15) },
            label = { Text(if (debt.side == "PIUTANG") "Diterima (${MoneyFormat.symbol(currency)})" else "Dibayar (${MoneyFormat.symbol(currency)})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val c = Calendar.getInstance().apply { timeInMillis = date }
                DatePickerDialog(context, { _, y, m, d ->
                    val nc = Calendar.getInstance()
                    nc.set(y, m, d, 0, 0, 0); nc.set(Calendar.MILLISECOND, 0)
                    date = nc.timeInMillis
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tanggal: ${Dates.dayLabel(date)}") }
        Spacer(Modifier.height(16.dp))
        SheetButton("Simpan pembayaran", pal.successFill, Color.White) {
            val v = amountText.toLongOrNull() ?: 0L
            when {
                v <= 0 -> vm.notify("Isi nominal pembayaran")
                v > remain -> vm.notify("Melebihi sisa ${MoneyFormat.format(remain, currency)}")
                else -> { vm.recordDebtPayment(debt, v, date); onClose() }
            }
        }
    }
}

// ---------- add / edit form sheet ----------

@Composable
private fun DebtFormSheet(
    vm: MoneyViewModel,
    editing: Debt?,
    defaultSide: String,
    currency: String,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    val pal = saldoColors
    val context = LocalContext.current
    var side by remember(editing) { mutableStateOf(editing?.side ?: defaultSide) }
    var person by remember(editing) { mutableStateOf(editing?.person ?: "") }
    var amountText by remember(editing) {
        mutableStateOf(editing?.let { MoneyFormat.minorToDigits(it.amountMinor) } ?: "")
    }
    var date by remember(editing) {
        mutableStateOf(editing?.date ?: Dates.startOfDay(System.currentTimeMillis()))
    }
    var due by remember(editing) { mutableStateOf(editing?.due) }
    var note by remember(editing) { mutableStateOf(editing?.note ?: "") }

    fun pickDate(current: Long, onPick: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = current }
        DatePickerDialog(context, { _, y, m, d ->
            val nc = Calendar.getInstance()
            nc.set(y, m, d, 0, 0, 0); nc.set(Calendar.MILLISECOND, 0)
            onPick(nc.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    DebtSheet(onClose = onClose) {
        Text(
            if (editing == null) "Tambah hutang / piutang" else "Ubah catatan",
            fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp, color = pal.ink
        )
        Spacer(Modifier.height(12.dp))
        SegControl(
            options = listOf("Piutang", "Hutang"),
            selected = if (side == "PIUTANG") 0 else 1,
            onSelect = { side = if (it == 0) "PIUTANG" else "HUTANG" }
        )
        Text(
            if (side == "PIUTANG") "Piutang = orang lain berutang ke kamu."
            else "Hutang = kamu berutang ke orang lain.",
            fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = person,
            onValueChange = { person = it },
            label = { Text("Nama orang") },
            placeholder = { Text("cth. Budi Santoso") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() }.take(15) },
            label = { Text("Nominal (${MoneyFormat.symbol(currency)})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { pickDate(date) { date = it } },
                modifier = Modifier.weight(1f)
            ) { Text("Pinjam: ${Dates.dayLabel(date)}") }
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { pickDate(due ?: System.currentTimeMillis()) { due = it } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tempo: ${due?.let { Dates.dayLabel(it) } ?: "—"}") }
                if (due != null) {
                    IconButton(
                        onClick = { due = null },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Hapus tempo",
                            tint = pal.muted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Catatan / keperluan") },
            placeholder = { Text("cth. Pinjam untuk servis motor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        SheetButton(
            if (editing == null) "Simpan catatan" else "Simpan perubahan",
            pal.accent, pal.accentInk
        ) {
            val amount = amountText.toLongOrNull() ?: 0L
            when {
                person.isBlank() -> vm.notify("Isi nama orang dulu")
                amount <= 0 -> vm.notify("Isi nominal dulu")
                else -> {
                    vm.saveDebt(
                        Debt(
                            id = editing?.id ?: 0,
                            side = side,
                            person = person.trim(),
                            amountMinor = amount,
                            note = note.trim(),
                            date = date,
                            due = due
                        ),
                        editing != null
                    )
                    onSaved(side)
                }
            }
        }
        if (editing != null) {
            Text(
                "Riwayat pembayaran tetap tersimpan saat catatan diubah.",
                fontFamily = Jakarta, fontSize = 12.sp, color = pal.faint,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
