package com.jiko.moneytracker.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.SavingsGoal
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import java.util.Calendar
import kotlin.math.roundToInt

private val GOAL_COLORS = listOf("#FF5722", "#00B0FF", "#FF9100", "#7C4DFF", "#00C853", "#FF4081")

/** Savings goals (Target Tabungan): web "goals" screen. */
@Composable
fun GoalsScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val goals by vm.goals.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    val accMap = remember(accounts) { accounts.associateBy { it.id } }

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var filling by remember { mutableStateOf<SavingsGoal?>(null) }

    val totalTarget = remember(goals) { goals.sumOf { it.targetMinor } }
    val totalSaved = remember(goals) { goals.sumOf { it.savedMinor } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            TopBar(
                title = "Target Tabungan",
                badge = "BARU",
                onBack = onBack,
                trailing = {
                    IconBtn(Icons.Filled.Add, onClick = {
                        editing = null; showForm = true
                    }, desc = "Tambah target")
                }
            )
        }
        item {
            Text(
                "Nabung per tujuan, terpisah dari saldo harian.",
                fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))
        }
        // Total card
        item {
            SaldoCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("Total terkumpul", fontFamily = Jakarta,
                        fontWeight = FontWeight.Medium, fontSize = 13.sp, color = pal.muted)
                    Text(
                        MoneyFormat.format(totalSaved, currency),
                        fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp, color = pal.ink, style = tnumStyle
                    )
                }
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    progress = {
                        if (totalTarget > 0) (totalSaved.toDouble() / totalTarget).toFloat() else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(99.dp)),
                    color = pal.successFill,
                    trackColor = pal.surface3,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "dari total target ${MoneyFormat.format(totalTarget, currency)}",
                    fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                    style = tnumStyle
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        // Goal rows
        item {
            if (goals.isEmpty()) {
                EmptyState(
                    title = "Belum ada target",
                    sub = "Buat target pertamamu untuk mulai menabung per tujuan.",
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            } else {
                DividerList(
                    items = goals,
                    key = { it.id },
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) { g ->
                    val pct = if (g.targetMinor > 0)
                        g.savedMinor.toDouble() / g.targetMinor else 0.0
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            editing = g; showForm = true
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressRing(
                            pct = pct.toFloat(),
                            color = colorOf(g.colorHex),
                            size = 52.dp, stroke = 9.dp
                        ) {
                            Text(
                                "${(pct * 100).roundToInt()}%",
                                fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp, color = pal.ink,
                                style = tnumStyle
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp, color = pal.ink)
                            Text(
                                "${MoneyFormat.format(g.savedMinor, currency)} dari ${MoneyFormat.format(g.targetMinor, currency)}",
                                fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted,
                                style = tnumStyle
                            )
                            Text(
                                "Target ${Dates.dayLabel(g.deadline)} · dari ${accMap[g.accountId]?.name ?: "—"}",
                                fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted
                            )
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(pal.accentSoft)
                                .clickable { filling = g }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("+ Isi", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp, color = pal.accent)
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        GoalFormDialog(
            vm = vm,
            editing = editing,
            onClose = { showForm = false }
        )
    }
    filling?.let { g ->
        GoalFillDialog(vm = vm, goal = g, onClose = { filling = null })
    }
}

/** Create / edit goal: name, target amount, deadline, account. */
@Composable
fun GoalFormDialog(vm: MoneyViewModel, editing: SavingsGoal?, onClose: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val goals by vm.goals.collectAsState()
    val currency by vm.currency.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf(editing?.name ?: "") }
    var targetText by remember {
        mutableStateOf(editing?.let { MoneyFormat.minorToDigits(it.targetMinor) } ?: "")
    }
    var deadline by remember {
        mutableStateOf(editing?.deadline ?: defaultDeadline())
    }
    var accountId by remember { mutableStateOf(editing?.accountId ?: accounts.firstOrNull()?.id ?: -1) }
    var accMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (editing == null) "Target tabungan baru" else "Ubah target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama target") },
                    placeholder = { Text("cth. Motor baru") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    targetText,
                    { targetText = it.filter { ch -> ch.isDigit() }.take(15) },
                    label = { Text("Target (${MoneyFormat.symbol(currency)})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = deadline }
                        DatePickerDialog(context, { _, y, m, d ->
                            val nc = Calendar.getInstance()
                            nc.set(y, m, d, 0, 0, 0); nc.set(Calendar.MILLISECOND, 0)
                            deadline = nc.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                            c.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tenggat: ${Dates.dayLabel(deadline)}") }
                Box {
                    OutlinedButton({ accMenu = true }, Modifier.fillMaxWidth()) {
                        Text("Akun: ${accounts.firstOrNull { it.id == accountId }?.name ?: "—"}")
                    }
                    DropdownMenu(accMenu, { accMenu = false }) {
                        accounts.forEach { a ->
                            DropdownMenuItem({ Text(a.name) }, { accountId = a.id; accMenu = false })
                        }
                    }
                }
                if (editing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hapus target", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = targetText.toLongOrNull() ?: 0L
                if (name.isBlank()) { vm.notify("Isi nama target"); return@TextButton }
                if (target <= 0) { vm.notify("Isi nominal target"); return@TextButton }
                val color = editing?.colorHex
                    ?: GOAL_COLORS[(goals.size) % GOAL_COLORS.size]
                vm.saveGoal(
                    SavingsGoal(
                        id = editing?.id ?: 0,
                        name = name.trim(),
                        targetMinor = target,
                        savedMinor = editing?.savedMinor ?: 0,
                        colorHex = color,
                        accountId = if (accountId > 0) accountId
                        else accounts.firstOrNull()?.id ?: 0,
                        deadline = deadline
                    ),
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
            title = { Text("Hapus target?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteGoal(editing); onClose() }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

/** Add money to a goal (web openGoalFill). */
@Composable
fun GoalFillDialog(vm: MoneyViewModel, goal: SavingsGoal, onClose: () -> Unit) {
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    var amountText by remember { mutableStateOf("250000") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Isi tabungan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${goal.name} · terkumpul ${MoneyFormat.format(goal.savedMinor, currency)} dari ${MoneyFormat.format(goal.targetMinor, currency)}",
                    fontSize = 13.sp, color = pal.muted
                )
                OutlinedTextField(
                    amountText,
                    { amountText = it.filter { ch -> ch.isDigit() }.take(15) },
                    label = { Text("Jumlah (${MoneyFormat.symbol(currency)})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toLongOrNull() ?: 0L
                if (amt <= 0) { vm.notify("Isi jumlah dulu"); return@TextButton }
                vm.fillGoal(goal, amt)
                onClose()
            }) {
                Text("Tambahkan", color = pal.successText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}

private fun defaultDeadline(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, 6)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}
