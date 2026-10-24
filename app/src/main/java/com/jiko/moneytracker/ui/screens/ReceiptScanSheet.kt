package com.jiko.moneytracker.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Txn
import com.jiko.moneytracker.ui.components.ReceiptImage
import com.jiko.moneytracker.ui.components.SelectorChip
import com.jiko.moneytracker.ui.components.colorOf
import com.jiko.moneytracker.ui.components.iconFor
import com.jiko.moneytracker.ui.components.tnumStyle
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.Dates
import com.jiko.moneytracker.util.MoneyFormat
import com.jiko.moneytracker.util.ParsedReceipt
import com.jiko.moneytracker.util.ReceiptOcr
import com.jiko.moneytracker.util.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * Pindai Struk — receipt scan flow mirroring the web version:
 * pilih (Foto struk / Dari galeri) -> memindai (on-device ML Kit OCR) -> hasil pindaian.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceiptScanSheet(vm: MoneyViewModel, onClose: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val currency by vm.currency.collectAsState()
    val pal = saldoColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) } // 0 pilih, 1 memindai, 2 hasil
    var imageFile by remember { mutableStateOf<File?>(null) }
    var statusIdx by remember { mutableStateOf(0) }
    var ocrFailed by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<ParsedReceipt?>(null) }

    // Review fields
    var store by remember { mutableStateOf("") }
    var dateTs by remember { mutableStateOf(System.currentTimeMillis()) }
    var totalDigits by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf(-1L) }

    val expenseCats = remember(categories) {
        categories.filter { it.kind != "INCOME" && it.parentId == null }
    }

    fun startOcr(file: File) {
        imageFile = file
        ocrFailed = false
        statusIdx = 0
        step = 1
        scope.launch {
            val started = System.currentTimeMillis()
            val result = try {
                val lines = ReceiptOcr.recognizeLines(context, Uri.fromFile(file))
                withContext(Dispatchers.Default) { ReceiptParser.parse(lines) }
            } catch (e: Exception) {
                null
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 1200) delay(1200 - elapsed) // let the scan animation read
            parsed = result
            ocrFailed = result == null
            store = result?.merchant ?: ""
            dateTs = result?.dateTs ?: System.currentTimeMillis()
            totalDigits = result?.total?.toString() ?: ""
            categoryId = expenseCats.firstOrNull { it.name.equals("Belanja", true) }?.id
                ?: expenseCats.firstOrNull()?.id
            accountId = vm.prefs.lastAccountId.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id ?: -1
            step = 2
        }
    }

    var pendingFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val f = pendingFile
        pendingFile = null
        if (ok && f != null) startOcr(f)
        else f?.delete()
    }
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { copyToReceipts(context, uri) }
                if (f != null) startOcr(f) else vm.notify("Gagal membaca foto")
            }
        }
    }

    LaunchedEffect(step) {
        while (step == 1) {
            delay(650)
            statusIdx = (statusIdx + 1) % 3
        }
    }

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        when (step) {
            0 -> {
                SheetHeader("Pindai struk", badge = "BARU", onClose = onClose)
                Text(
                    "Foto struk belanja — toko, tanggal, dan total terbaca otomatis. Kamu tinggal periksa hasilnya sebelum disimpan jadi transaksi.",
                    fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                // Stage placeholder
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(190.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Receipt, contentDescription = null,
                            tint = pal.faint, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Foto struk akan tampil di sini",
                            fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    ScanActionButton(
                        icon = Icons.Filled.PhotoCamera, label = "Foto struk",
                        modifier = Modifier.weight(1f)
                    ) {
                        val f = newReceiptFile(context)
                        pendingFile = f
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", f
                        )
                        takePicture.launch(uri)
                    }
                    ScanActionButton(
                        icon = Icons.Filled.PhotoLibrary, label = "Dari galeri",
                        modifier = Modifier.weight(1f)
                    ) {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                }
            }
            1 -> {
                SheetHeader("Memindai struk…", badge = null, onClose = onClose)
                val f = imageFile
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .height(300.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (f != null) {
                        ReceiptImage(
                            f.absolutePath,
                            modifier = Modifier.fillMaxSize(),
                            maxDim = 800,
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Animated scan line (web .scanline)
                    val transition = rememberInfiniteTransition(label = "scanline")
                    val frac by transition.animateFloat(
                        initialValue = 0.09f, targetValue = 0.89f,
                        animationSpec = infiniteRepeatable(
                            tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
                        ),
                        label = "frac"
                    )
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(frac))
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                                .height(3.dp).clip(RoundedCornerShape(99.dp))
                                .background(pal.accent)
                        )
                        Spacer(Modifier.weight(1f - frac))
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp, color = pal.accent
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        listOf("Membaca teks struk…", "Mencari total & tanggal…", "Mengenali kategori…")[statusIdx],
                        fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp, color = pal.muted
                    )
                }
            }
            else -> {
                // ---- Hasil pindaian (review) ----
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hasil pindaian", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp, color = pal.ink, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp))
                            .background(if (ocrFailed) pal.dangerSoft else pal.accentSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!ocrFailed) {
                                Icon(Icons.Filled.Check, contentDescription = null,
                                    tint = pal.accent, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                if (ocrFailed) "Isi manual" else "Terbaca otomatis",
                                fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                color = if (ocrFailed) MaterialTheme.colorScheme.error else pal.accent
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = pal.muted)
                    }
                }
                Text(
                    if (ocrFailed)
                        "Teks struk tidak terbaca jelas — periksa foto dan isi totalnya manual."
                    else "Periksa dan ubah bila perlu, lalu simpan.",
                    fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                val items = parsed?.items ?: emptyList()
                if (items.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            items.forEachIndexed { i, item ->
                                if (i > 0) HorizontalDivider(thickness = 1.dp, color = pal.line)
                                ScanItemRow(item.name, MoneyFormat.format(item.amount, currency))
                            }
                            HorizontalDivider(thickness = 1.dp, color = pal.line)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Jumlah item", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = pal.ink)
                                Text(
                                    MoneyFormat.format(items.sumOf { it.amount }, currency),
                                    fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, color = pal.ink, style = tnumStyle
                                )
                            }
                        }
                    }
                }

                // Attached photo strip
                imageFile?.let { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReceiptImage(
                            f.absolutePath,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                            maxDim = 200, contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Foto struk terlampir pada transaksi ini",
                            fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = store, onValueChange = { store = it },
                    label = { Text("Nama toko") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        Text(Dates.dayLabel(dateTs), fontSize = 12.5.sp)
                    }
                    OutlinedTextField(
                        value = totalDigits.takeIf { it.isNotBlank() }?.let {
                            MoneyFormat.format(it.toLongOrNull() ?: 0, currency)
                                .removePrefix(MoneyFormat.symbol(currency))
                        } ?: "",
                        onValueChange = { totalDigits = it.filter { ch -> ch.isDigit() }.take(12) },
                        label = { Text("Total (Rp)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = tnumStyle,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                FieldLabel("Kategori")
                // FlowRow: each chip is measured at its intrinsic width (capped at the
                // row width), so labels can never be squeezed into mid-word wraps and
                // chips never stretch. SelectorChip is fixed 40dp with ellipsis.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expenseCats.forEach { c ->
                        SelectorChip(
                            label = c.name,
                            selected = categoryId == c.id,
                            onClick = { categoryId = c.id },
                            leadingIcon = {
                                Icon(iconFor(c.iconKey), null, Modifier.size(15.dp), tint = colorOf(c.colorHex))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FieldLabel("Dibayar dari")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accounts.forEach { a ->
                        SelectorChip(
                            label = a.name,
                            selected = accountId == a.id,
                            onClick = { accountId = a.id },
                            leadingIcon = {
                                Icon(iconFor(a.iconKey), null, Modifier.size(15.dp), tint = colorOf(a.colorHex))
                            }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Save
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .shadow(2.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.successFill)
                        .clickable {
                            val total = totalDigits.toLongOrNull() ?: 0
                            if (total <= 0) { vm.notify("Periksa total struk dulu"); return@clickable }
                            if (accountId < 0) { vm.notify("Pilih akun dulu"); return@clickable }
                            if (categoryId == null) { vm.notify("Pilih kategori dulu"); return@clickable }
                            vm.saveTxn(
                                Txn(
                                    type = "EXPENSE",
                                    amountMinor = total,
                                    accountId = accountId,
                                    categoryId = categoryId,
                                    note = store.trim().ifBlank { "Struk belanja" },
                                    tags = "struk",
                                    timestamp = dateTs,
                                    receiptPath = imageFile?.absolutePath
                                ),
                                isEdit = false
                            )
                            onClose()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Simpan sebagai transaksi", fontFamily = Jakarta,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                }
                TextButton(
                    onClick = {
                        step = 0
                        parsed = null
                        imageFile = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text("Pindai ulang", fontFamily = Jakarta, fontSize = 13.5.sp, color = pal.muted)
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, badge: String?, onClose: () -> Unit) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp, color = pal.ink)
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
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = pal.muted)
        }
    }
}

@Composable
private fun ScanActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = saldoColors
    Box(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = pal.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp, color = pal.ink)
        }
    }
}

@Composable
private fun ScanItemRow(name: String, amountText: String) {
    val pal = saldoColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontFamily = Jakarta, fontSize = 13.sp, color = pal.ink,
            modifier = Modifier.weight(1f).padding(end = 12.dp), maxLines = 2)
        Text(amountText, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = pal.ink, style = tnumStyle)
    }
}

@Composable
private fun FieldLabel(text: String) {
    val pal = saldoColors
    Text(text, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, color = pal.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

private fun newReceiptFile(context: Context): File {
    val dir = File(context.filesDir, "receipts").apply { mkdirs() }
    return File(dir, "struk_${System.currentTimeMillis()}.jpg")
}

private fun copyToReceipts(context: Context, uri: Uri): File? = try {
    val f = newReceiptFile(context)
    val ok = context.contentResolver.openInputStream(uri)?.use { input ->
        f.outputStream().use { out -> input.copyTo(out) }
        true
    } ?: false
    if (ok) f else null
} catch (_: Exception) {
    null
}
