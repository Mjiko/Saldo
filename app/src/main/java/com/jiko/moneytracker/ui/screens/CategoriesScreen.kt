package com.jiko.moneytracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.data.Category
import com.jiko.moneytracker.ui.components.*
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors

/** A category acts as a root when it has no parent, or its parent no longer exists. */
private fun isRootCat(c: Category, all: List<Category>): Boolean =
    c.parentId == null || all.none { it.id == c.parentId }

private fun kindLabel(kind: String): String = when (kind) {
    "INCOME" -> "Pemasukan"
    "BOTH" -> "Pengeluaran & pemasukan"
    else -> "Pengeluaran"
}

/** Kelola Kategori — custom category manager, mirroring the web "categories" screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(vm: MoneyViewModel, onBack: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val txns by vm.txns.collectAsState()
    val pal = saldoColors

    var tab by remember { mutableStateOf(0) } // 0 = Pengeluaran, 1 = Pemasukan
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var session by remember { mutableStateOf(0) }

    // Web catsForTab: a tab shows its own side plus BOTH (so "Lainnya" is in both).
    val roots = remember(categories, tab) {
        categories.filter { c ->
            isRootCat(c, categories) &&
                if (tab == 0) c.kind != "INCOME" else c.kind != "EXPENSE"
        }
    }
    val children = remember(categories) {
        categories.filter { it.parentId != null }.groupBy { it.parentId }
    }

    fun openEditor(c: Category?) {
        editing = c
        session++
        showEditor = true
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Kelola Kategori", badge = "BARU", onBack = onBack)
        Text(
            "Buat kategori sendiri dengan ikon dan warna pilihanmu — langsung bisa dipakai " +
                "di semua layar. Kategori bawaan bisa diubah, tapi tidak bisa dihapus.",
            fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 2.dp)
        )
        SegControl(
            options = listOf("Pengeluaran", "Pemasukan"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp)
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = 8.dp)
        ) {
            if (roots.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Belum ada kategori", fontFamily = Jakarta,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = pal.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ketuk tombol di bawah untuk membuat kategori pertamamu di sisi ini.",
                        fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                SaldoCard(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    roots.forEachIndexed { idx, root ->
                        if (idx > 0) HorizontalDivider(color = pal.line)
                        CategoryBlock(
                            root = root,
                            subs = children[root.id] ?: emptyList(),
                            onClick = { openEditor(root) }
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 14.dp)) {
            Box(
                Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.accent)
                    .clickable { openEditor(null) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null,
                        tint = pal.accentInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tambah kategori", fontFamily = Jakarta,
                        fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = pal.accentInk)
                }
            }
        }
    }

    if (showEditor) {
        key(session) {
            val target = editing
            val refCount = remember(target, categories, txns) {
                if (target == null) 0 else {
                    val ids = setOf(target.id) +
                        categories.filter { it.parentId == target.id }.map { it.id }
                    txns.count { it.categoryId in ids }
                }
            }
            CategoryEditorSheet(
                vm = vm,
                editing = target,
                createKind = if (tab == 0) "EXPENSE" else "INCOME",
                all = categories,
                refCount = refCount,
                onClose = { showEditor = false }
            )
        }
    }
}

@Composable
private fun CategoryBlock(root: Category, subs: List<Category>, onClick: () -> Unit) {
    val pal = saldoColors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(root.iconKey, root.colorHex, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(root.name, fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, color = pal.ink)
                val meta = buildList {
                    if (subs.isNotEmpty()) add("${subs.size} subkategori")
                    add(kindLabel(root.kind))
                }.joinToString(" · ")
                Text(meta, fontFamily = Jakarta, fontSize = 12.sp, color = pal.muted)
            }
            CatBadge(custom = root.isCustom)
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = pal.faint, modifier = Modifier.size(20.dp))
        }
        subs.forEach { sub ->
            Row(
                Modifier.fillMaxWidth().padding(start = 52.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .background(pal.faint.copy(alpha = 0.6f))
                )
                Spacer(Modifier.width(10.dp))
                Text(sub.name, fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted)
            }
        }
    }
}

@Composable
private fun CatBadge(custom: Boolean) {
    val pal = saldoColors
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .background(
                if (custom) pal.accentSoft
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            if (custom) "Kustom" else "Bawaan",
            fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 10.5.sp,
            color = if (custom) pal.accent else pal.muted
        )
    }
}

// ---------- editor sheet ----------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorSheet(
    vm: MoneyViewModel,
    editing: Category?,
    createKind: String,
    all: List<Category>,
    refCount: Int,
    onClose: () -> Unit
) {
    val pal = saldoColors
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var iconKey by remember { mutableStateOf(editing?.iconKey ?: "tag") }
    var colorHex by remember { mutableStateOf(editing?.colorHex ?: "#FF5722") }
    var stagedSubs by remember {
        mutableStateOf(
            if (editing == null) emptyList()
            else all.filter { it.parentId == editing.id }.map { it.name }
        )
    }
    var newSub by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val kind = editing?.kind ?: createKind
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun addSub() {
        val v = newSub.trim()
        if (v.isEmpty()) { vm.notify("Isi nama subkategori dulu"); return }
        if (stagedSubs.any { it.equals(v, ignoreCase = true) }) {
            vm.notify("Subkategori itu sudah ada"); return
        }
        stagedSubs = stagedSubs + v
        newSub = ""
    }

    fun save() {
        val nm = name.trim()
        if (nm.isEmpty()) { vm.notify("Isi nama kategori dulu"); return }
        val dup = all.any { c ->
            c.id != (editing?.id ?: -1L) && isRootCat(c, all) &&
                c.name.trim().equals(nm, ignoreCase = true) &&
                (kind == "BOTH" || c.kind == "BOTH" || c.kind == kind)
        }
        if (dup) { vm.notify("Nama kategori sudah dipakai di sisi ini"); return }
        if (editing == null) {
            vm.saveCategory(
                Category(name = nm, iconKey = iconKey, colorHex = colorHex, kind = kind),
                isEdit = false
            )
        } else {
            val updated = editing.copy(name = nm, iconKey = iconKey, colorHex = colorHex)
            vm.saveCategory(updated, isEdit = true)
            vm.syncSubcategories(updated, stagedSubs)
        }
        onClose()
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
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
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 28.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (editing == null) "Kategori baru" else "Ubah kategori",
                    fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp, color = pal.ink
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = pal.muted)
                }
            }
            Text(
                "Sisi: ${kindLabel(kind)}",
                fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
            )
            // Live preview (web: 56dp tile + name)
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIcon(iconKey, colorHex, size = 56)
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        name.ifBlank { "Nama kategori" },
                        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp, color = pal.ink
                    )
                    Text(
                        "Pratinjau tampilan kategori",
                        fontFamily = Jakarta, fontSize = 12.5.sp, color = pal.muted
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Nama kategori", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = pal.muted)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("cth. Peliharaan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Ikon", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = pal.muted)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(CATEGORY_ICON_CHOICES) { key ->
                    val sel = iconKey == key
                    Box(
                        Modifier.aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (sel) colorOf(colorHex)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { iconKey = key },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            iconFor(key), contentDescription = key,
                            tint = if (sel) Color.White else pal.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Warna", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = pal.muted)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CATEGORY_COLOR_CHOICES.forEach { hex ->
                    val sel = colorHex.equals(hex, ignoreCase = true)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(colorOf(hex))
                            .then(
                                if (sel) Modifier.border(2.dp, pal.ink, CircleShape)
                                else Modifier
                            )
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sel) Icon(
                            Icons.Filled.Check, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            // Subcategories — edit mode only (web behaviour)
            if (editing != null) {
                Spacer(Modifier.height(18.dp))
                Text("Subkategori", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = pal.muted)
                Spacer(Modifier.height(6.dp))
                if (stagedSubs.isEmpty()) {
                    Text("Belum ada subkategori.", fontFamily = Jakarta,
                        fontSize = 13.sp, color = pal.muted)
                } else {
                    stagedSubs.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s, fontFamily = Jakarta, fontSize = 14.sp,
                                color = pal.ink, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { stagedSubs = stagedSubs - s },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Hapus subkategori",
                                    tint = pal.muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newSub, onValueChange = { newSub = it },
                        placeholder = { Text("Nama subkategori baru") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(pal.accentSoft)
                            .clickable { addSub() }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text("Tambah", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp, color = pal.accent)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.accent)
                    .clickable { save() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (editing == null) "Tambah kategori" else "Simpan perubahan",
                    fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp, color = pal.accentInk
                )
            }
            if (editing != null) {
                if (editing.isCustom) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pal.dangerSoft)
                            .clickable { confirmDelete = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Hapus kategori",
                            fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp, color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Text(
                        "Kategori bawaan tidak bisa dihapus.",
                        fontFamily = Jakarta, fontSize = 12.sp, color = pal.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }
        }
    }

    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus kategori?") },
            text = {
                Text(
                    "Kategori ${editing.name} akan dihapus. " +
                        (if (refCount > 0)
                            "$refCount transaksi yang memakai kategori ini akan dipindahkan " +
                                "ke kategori \"Lainnya\"."
                        else "Belum ada transaksi yang memakai kategori ini.") +
                        " Anggaran dan jadwal berulang yang merujuk kategori ini juga ikut " +
                        "dipindahkan ke \"Lainnya\", dan subkategorinya ikut dihapus."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(editing)
                    confirmDelete = false
                    onClose()
                }) { Text("Ya, hapus kategori", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Batal") }
            }
        )
    }
}
