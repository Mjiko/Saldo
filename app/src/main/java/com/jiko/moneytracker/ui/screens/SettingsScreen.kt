package com.jiko.moneytracker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.AutoBackupFreq
import com.jiko.moneytracker.util.MoneyFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings (Pengaturan) + backup/export/import + app lock. Categories live in CategoriesScreen. */
@Composable
fun SettingsScreen(vm: MoneyViewModel, onBack: () -> Unit, onOpenCategories: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("Pengaturan", onBack = onBack)
        PrefsPane(vm, onOpenCategories)
    }
}

@Composable
private fun PrefsPane(vm: MoneyViewModel, onOpenCategories: () -> Unit) {
    val currency by vm.currency.collectAsState()
    val darkMode by vm.darkMode.collectAsState()
    val monthStart by vm.monthStart.collectAsState()
    val profileName by vm.profileName.collectAsState()
    val driveInfo by vm.driveInfo.collectAsState()
    val autoBackup by vm.autoBackup.collectAsState()
    val pal = saldoColors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // A background auto-backup may have updated Prefs while this screen was away.
    LaunchedEffect(Unit) { vm.refreshAutoBackupState() }

    var pinDialog by remember { mutableStateOf(false) }
    var bioConfirm by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf(false) }
    var lockOn by remember { mutableStateOf(vm.prefs.lockEnabled) }
    var bioOn by remember { mutableStateOf(vm.prefs.biometricEnabled) }
    var monthMenu by remember { mutableStateOf(false) }
    var curMenu by remember { mutableStateOf(false) }

    val importCsv = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { vm.importCsv(it) {} }
    }
    val restoreJson = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let { vm.restore(it) {} }
    }
    val driveCreate = rememberLauncherForActivityResult(CreateDocument()) { uri ->
        uri?.let { vm.driveBackupToNew(it) }
    }
    // Only used for the auto-backup failure alert; backups run regardless of the answer.
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Profil
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Profil")
            }
            DividerList(
                items = listOf("name"),
                key = { it },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                MenuRow(
                    icon = Icons.Filled.Person,
                    iconBg = pal.accentSoft,
                    name = profileName.trim().ifBlank { "Jiko" },
                    desc = "Nama untuk sapaan di Beranda · ketuk untuk mengubah",
                    onClick = { nameDialog = true }
                )
            }
        }
        // Kategori
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Kategori")
            }
            DividerList(
                items = listOf("cats"),
                key = { it },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) {
                MenuRow(
                    icon = Icons.Filled.LocalOffer,
                    iconBg = pal.accentSoft,
                    name = "Kelola Kategori",
                    desc = "Buat kategori sendiri dengan ikon & warna pilihanmu",
                    onClick = onOpenCategories
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Tampilan")
            }
            SegControl(
                options = listOf("Sistem", "Terang", "Gelap"),
                selected = darkMode,
                onSelect = { vm.setDarkMode(it) },
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
        item {
            DividerList(
                items = listOf("month", "currency"),
                key = { it },
                modifier = Modifier.padding(horizontal = 18.dp).padding(top = 12.dp)
            ) { k ->
                when (k) {
                    "month" -> Box {
                        MenuRow(
                            icon = Icons.Filled.CalendarMonth,
                            iconBg = pal.surface3,
                            name = "Awal bulan budget",
                            desc = "Periode mulai tanggal $monthStart",
                            onClick = { monthMenu = true }
                        )
                        DropdownMenu(monthMenu, { monthMenu = false }) {
                            (1..28).toList().chunked(7).forEach { row ->
                                Row {
                                    row.forEach { d ->
                                        DropdownMenuItem(
                                            text = { Text("$d") },
                                            onClick = { vm.setMonthStart(d); monthMenu = false },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> Box {
                        MenuRow(
                            icon = Icons.Filled.Language,
                            iconBg = pal.surface3,
                            name = "Mata uang dasar",
                            desc = currency,
                            onClick = { curMenu = true }
                        )
                        DropdownMenu(curMenu, { curMenu = false }) {
                            MoneyFormat.CURRENCIES.forEach { c ->
                                DropdownMenuItem({ Text("$c (${MoneyFormat.symbol(c)})") },
                                    { vm.setCurrency(c); curMenu = false })
                            }
                        }
                    }
                }
            }
        }
        // Keamanan
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Keamanan")
            }
            DividerList(
                items = listOf("pin", "bio"),
                key = { it },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) { k ->
                when (k) {
                    "pin" -> MenuRow(
                        icon = Icons.Filled.Lock,
                        iconBg = pal.accentSoft,
                        name = "Kunci PIN",
                        desc = if (vm.prefs.hasPin()) "Aktif — aplikasi terkunci saat dibuka"
                        else "Kunci aplikasi dengan PIN 4 digit",
                        right = {
                            SaldoSwitch(checked = lockOn && vm.prefs.hasPin(), onChecked = { on ->
                                if (on && !vm.prefs.hasPin()) pinDialog = true
                                else { lockOn = on; vm.prefs.lockEnabled = on }
                            })
                            Spacer(Modifier.width(4.dp))
                        },
                        onClick = { if (!vm.prefs.hasPin()) pinDialog = true }
                    )
                    else -> MenuRow(
                        icon = Icons.Filled.Fingerprint,
                        iconBg = pal.surface3,
                        name = "Buka dengan biometrik",
                        desc = "Sidik jari / wajah",
                        right = {
                            SaldoSwitch(checked = bioOn, onChecked = { on ->
                                if (on) bioConfirm = true
                                else { bioOn = false; vm.prefs.biometricEnabled = false }
                            })
                            Spacer(Modifier.width(4.dp))
                        },
                        onClick = {}
                    )
                }
            }
            if (vm.prefs.hasPin()) {
                TextButton(
                    onClick = { pinDialog = true },
                    modifier = Modifier.padding(horizontal = 18.dp)
                ) { Text("Ubah PIN") }
            }
        }
        // Data
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Data")
            }
            DividerList(
                items = listOf("csv", "csvimp", "json", "restore"),
                key = { it },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) { k ->
                when (k) {
                    "csv" -> MenuRow(
                        icon = Icons.Filled.FileDownload,
                        iconBg = pal.successFill.copy(alpha = 0.13f),
                        name = "Ekspor CSV",
                        desc = "Semua transaksi sebagai berkas .csv",
                        onClick = { vm.exportCsv {} }
                    )
                    "csvimp" -> MenuRow(
                        icon = Icons.Filled.FileUpload,
                        iconBg = pal.warningFill.copy(alpha = 0.15f),
                        name = "Impor CSV",
                        desc = "Format: date,type,amount,account,category,note,tags",
                        onClick = { importCsv.launch(arrayOf("text/*")) }
                    )
                    "json" -> MenuRow(
                        icon = Icons.Filled.Backup,
                        iconBg = Color(0xFF00B0FF).copy(alpha = 0.13f),
                        name = "Cadangkan (JSON)",
                        desc = "Simpan seluruh data Saldo",
                        onClick = { vm.backup {} }
                    )
                    else -> MenuRow(
                        icon = Icons.Filled.SettingsBackupRestore,
                        iconBg = pal.warningFill.copy(alpha = 0.15f),
                        name = "Pulihkan cadangan",
                        desc = "Muat berkas .json — dari berkas lokal atau Google Drive",
                        onClick = { restoreJson.launch(arrayOf("application/*")) }
                    )
                }
            }
        }
        // Google Drive (via system picker, no sign-in needed)
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Google Drive")
                if (driveInfo.uri.isNotBlank() && driveInfo.last > 0) {
                    Text(
                        "Cadangan terakhir: " + driveLastLabel(driveInfo.last),
                        fontFamily = Jakarta, fontWeight = FontWeight.Medium,
                        fontSize = 12.sp, color = pal.muted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }
            }
            val keys = if (driveInfo.uri.isBlank()) listOf("new")
                else listOf("now", "change", "forget")
            DividerList(
                items = keys,
                key = { it },
                modifier = Modifier.padding(horizontal = 18.dp)
            ) { k ->
                when (k) {
                    "new" -> MenuRow(
                        icon = Icons.Filled.CloudUpload,
                        iconBg = Color(0xFF00B0FF).copy(alpha = 0.13f),
                        name = "Cadangkan ke Drive",
                        desc = "Pilih lokasi berkas di Google Drive lewat pemilih file",
                        onClick = { driveCreate.launch(suggestedDriveName()) }
                    )
                    "now" -> MenuRow(
                        icon = Icons.Filled.CloudUpload,
                        iconBg = Color(0xFF00B0FF).copy(alpha = 0.13f),
                        name = "Cadangkan sekarang",
                        desc = "Langsung ke: ${driveInfo.name.ifBlank { "berkas Drive" }}",
                        onClick = {
                            vm.driveBackupNow { ok ->
                                if (!ok) scope.launch { driveCreate.launch(suggestedDriveName()) }
                            }
                        }
                    )
                    "change" -> MenuRow(
                        icon = Icons.Filled.CloudSync,
                        iconBg = pal.surface3,
                        name = "Ganti lokasi",
                        desc = "Pilih berkas/lokasi Drive yang baru",
                        onClick = { driveCreate.launch(suggestedDriveName()) }
                    )
                    else -> MenuRow(
                        icon = Icons.Filled.CloudOff,
                        iconBg = pal.warningFill.copy(alpha = 0.15f),
                        name = "Lupakan lokasi",
                        desc = "Hapus lokasi Drive yang diingat",
                        onClick = { vm.forgetDriveLocation() }
                    )
                }
            }
            // Backup otomatis — extends the manual Drive backup above (mirrors the web design)
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                val hasDrive = driveInfo.uri.isNotBlank()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Backup otomatis", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp, color = pal.ink
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (hasDrive) "Cadangkan otomatis ke Drive sesuai jadwal di bawah"
                            else "Pilih lokasi Drive dulu untuk mengaktifkan backup otomatis",
                            fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.alpha(if (hasDrive) 1f else 0.45f)) {
                        SaldoSwitch(checked = autoBackup.enabled, onChecked = { on ->
                            if (!hasDrive) return@SaldoSwitch
                            vm.setAutoBackupEnabled(on)
                            // Ask once, in context, so a failed backup can alert (API 33+).
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        })
                        // Shield swallows taps while disabled (no Drive location yet).
                        if (!hasDrive) Box(Modifier.matchParentSize().clickable { })
                    }
                }
                if (autoBackup.enabled) {
                    Spacer(Modifier.height(10.dp))
                    SegControl(
                        options = listOf("Setiap perubahan", "Harian", "Mingguan"),
                        selected = autoBackup.freq.ordinal,
                        onSelect = { vm.setAutoBackupFreq(AutoBackupFreq.values()[it]) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    autoBackupStatus(autoBackup.enabled, autoBackup.freq, autoBackup.lastAuto),
                    fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted
                )
                if (autoBackup.enabled && autoBackup.failed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Backup otomatis terakhir gagal — periksa lokasi Drive di atas, lalu coba Cadangkan sekarang.",
                        fontFamily = Jakarta, fontSize = 12.sp, color = pal.warningText
                    )
                }
            }
        }
        // Tentang
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                SectionTitle("Tentang")
                SaldoCard(Modifier.fillMaxWidth()) {
                    Text("Saldo", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = pal.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pelacak pengeluaran & anggaran yang minimalis, sepenuhnya luring, tanpa akun. Versi 1.7 · Mata uang: $currency.",
                        fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Dibuat oleh Jiko",
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, color = pal.ink
                    )
                }
            }
        }
    }

    // themeSeg dropdown anchor: the SegControl item hosts it inline instead.
    // (Theme is set directly from the SegControl above; no dropdown needed.)
    if (nameDialog) {
        ProfileNameDialog(
            current = profileName,
            onClose = { nameDialog = false },
            onSave = { vm.setProfileName(it); nameDialog = false }
        )
    }
    if (pinDialog) {
        PinSetupDialog(
            onClose = { pinDialog = false },
            onSet = { pin ->
                vm.prefs.setPin(pin)
                lockOn = true; vm.prefs.lockEnabled = true
                vm.notify("PIN dipasang, kunci aktif")
                pinDialog = false
            }
        )
    }
    if (bioConfirm) {
        AlertDialog(
            onDismissRequest = { bioConfirm = false },
            title = { Text("Aktifkan biometrik?") },
            text = { Text("Anda tetap bisa memakai PIN jika biometrik gagal.") },
            confirmButton = {
                TextButton(onClick = {
                    bioOn = true; vm.prefs.biometricEnabled = true
                    bioConfirm = false
                }) { Text("Aktifkan") }
            },
            dismissButton = { TextButton(onClick = { bioConfirm = false }) { Text("Batal") } }
        )
    }
}

/** Suggested Drive backup file name, e.g. saldo-backup-20261020-1319.json */
private fun suggestedDriveName(): String =
    "saldo-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"

private fun driveLastLabel(ts: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("in", "ID")).format(Date(ts))

/** Status line under the Backup otomatis controls (copy mirrors the approved web design). */
private fun autoBackupStatus(enabled: Boolean, freq: AutoBackupFreq, lastAuto: Long): String {
    if (!enabled) return "Backup otomatis tidak aktif — cadangan hanya manual."
    val last = if (lastAuto > 0) "Backup otomatis terakhir: " + driveLastLabel(lastAuto)
        else "Backup otomatis terakhir: Belum pernah"
    val days = when (freq) {
        AutoBackupFreq.DAILY -> 1L
        AutoBackupFreq.WEEKLY -> 7L
        AutoBackupFreq.ON_CHANGE -> return last
    }
    // WorkManager's phase starts at (re)schedule time; after a run it repeats every
    // `days`, so the next run is estimated from the last one (or from now).
    val now = System.currentTimeMillis()
    var next = (if (lastAuto > 0) lastAuto else now) + days * 86_400_000L
    if (next <= now) next = now + days * 86_400_000L
    val nextLabel = SimpleDateFormat("EEEE d MMM, HH:mm", Locale("in", "ID")).format(Date(next))
    return "$last\nBerikutnya: $nextLabel"
}

@Composable
private fun ProfileNameDialog(current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Nama profil") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Nama") },
                placeholder = { Text("Jiko") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}

@Composable
private fun PinSetupDialog(onClose: () -> Unit, onSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Atur PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("PIN (4–6 digit)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(confirm, { confirm = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Ulangi PIN") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> err = "PIN minimal 4 digit"
                    pin != confirm -> err = "PIN tidak sama"
                    else -> onSet(pin)
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
