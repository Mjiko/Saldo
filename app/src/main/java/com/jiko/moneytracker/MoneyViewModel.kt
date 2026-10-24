package com.jiko.moneytracker

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiko.moneytracker.data.*
import com.jiko.moneytracker.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MoneyViewModel(app: Application) : AndroidViewModel(app) {
    private val db = MoneyDatabase.get(app)
    val prefs = Prefs(app)

    val txns: StateFlow<List<Txn>> = db.txnDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<Category>> = db.categoryDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val accounts: StateFlow<List<Account>> = db.accountDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets: StateFlow<List<Budget>> = db.budgetDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recurrings: StateFlow<List<Recurring>> = db.recurringDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals: StateFlow<List<SavingsGoal>> = db.goalDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val debts: StateFlow<List<Debt>> = db.debtDao().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val debtPayments: StateFlow<List<DebtPayment>> = db.debtDao().allPayments()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currency = MutableStateFlow(prefs.baseCurrency)
    val currency: StateFlow<String> = _currency.asStateFlow()
    private val _profileName = MutableStateFlow(prefs.profileName)
    val profileName: StateFlow<String> = _profileName.asStateFlow()

    /** (uri, displayName, lastBackupMillis) of the remembered Drive backup document. */
    data class DriveInfo(val uri: String, val name: String, val last: Long)
    private val _driveInfo = MutableStateFlow(
        DriveInfo(prefs.driveBackupUri, prefs.driveBackupName, prefs.driveBackupLast)
    )
    val driveInfo: StateFlow<DriveInfo> = _driveInfo.asStateFlow()

    /** (enabled, frequency, lastAutoMillis, lastFailed) for the automatic Drive backup. */
    data class AutoBackupInfo(
        val enabled: Boolean, val freq: AutoBackupFreq, val lastAuto: Long, val failed: Boolean
    )
    private fun readAutoBackup() = AutoBackupInfo(
        prefs.autoBackupEnabled,
        AutoBackupFreq.fromStored(prefs.autoBackupFreq),
        prefs.lastAutoBackupAt,
        prefs.lastAutoBackupFailed
    )
    private val _autoBackup = MutableStateFlow(readAutoBackup())
    val autoBackup: StateFlow<AutoBackupInfo> = _autoBackup.asStateFlow()
    private fun refreshAutoBackup() { _autoBackup.value = readAutoBackup() }

    /** Re-read auto-backup state (a background worker may have updated Prefs meanwhile). */
    fun refreshAutoBackupState() = refreshAutoBackup()

    fun setAutoBackupEnabled(on: Boolean) {
        if (on && prefs.driveBackupUri.isBlank()) return // UI disables the switch; guard anyway
        prefs.autoBackupEnabled = on
        if (on) AutoBackupScheduler.reschedule(getApplication())
        else AutoBackupScheduler.cancelAll(getApplication())
        refreshAutoBackup()
    }

    fun setAutoBackupFreq(f: AutoBackupFreq) {
        prefs.autoBackupFreq = f.name
        AutoBackupScheduler.reschedule(getApplication())
        refreshAutoBackup()
    }
    private val _darkMode = MutableStateFlow(prefs.darkMode)
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()
    private val _monthStart = MutableStateFlow(prefs.monthStartDay)
    val monthStart: StateFlow<Int> = _monthStart.asStateFlow()

    private val _toast = MutableSharedFlow<String>()
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** Set by Home donut drill-down; TransactionsScreen consumes it once. */
    private val _drillCategory = MutableStateFlow<Long?>(null)
    val drillCategory: StateFlow<Long?> = _drillCategory.asStateFlow()
    fun drillToCategory(id: Long) { _drillCategory.value = id }
    fun clearDrill() { _drillCategory.value = null }

    private val _drillAccount = MutableStateFlow<Long?>(null)
    val drillAccount: StateFlow<Long?> = _drillAccount.asStateFlow()
    fun drillToAccount(id: Long) { _drillAccount.value = id }
    fun clearAccountDrill() { _drillAccount.value = null }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Seed.run(db)
            applyRecurringDue()
        }
    }

    fun notify(msg: String) = viewModelScope.launch { _toast.emit(msg) }

    // ---------- transactions ----------
    fun saveTxn(t: Txn, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.txnDao().update(t) else db.txnDao().insert(t)
        if (t.accountId > 0) prefs.lastAccountId = t.accountId
        if (t.note.isNotBlank() && t.categoryId != null) {
            prefs.rememberCategory(t.note, t.categoryId)
            prefs.rememberRepeat(t.note, t.amountMinor)
        }
        AutoBackupScheduler.onDataChanged(getApplication())
        notify(if (isEdit) "Transaksi diperbarui" else "Tersimpan ✓")
    }

    fun deleteTxns(ids: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        db.txnDao().deleteIds(ids)
        AutoBackupScheduler.onDataChanged(getApplication())
        notify("${ids.size} transaksi dihapus")
    }

    fun duplicateTxn(t: Txn) = viewModelScope.launch(Dispatchers.IO) {
        db.txnDao().insert(t.copy(id = 0, timestamp = System.currentTimeMillis()))
        AutoBackupScheduler.onDataChanged(getApplication())
        notify("Transaksi diduplikasi")
    }

    // ---------- categories (Kelola Kategori) ----------
    /** Creates are always Kustom; edits preserve the row (incl. its Bawaan/Kustom flag). */
    fun saveCategory(c: Category, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.categoryDao().update(c)
        else db.categoryDao().insert(
            c.copy(isCustom = true, sortOrder = (categories.value.maxOfOrNull { it.sortOrder } ?: 0) + 1)
        )
        AutoBackupScheduler.onDataChanged(getApplication())
        notify(if (isEdit) "Kategori diperbarui" else "Kategori ditambahkan")
    }

    /**
     * Delete a category (custom only — the UI hides this for Bawaan): transactions,
     * budgets and recurring entries that referenced it — or any of its subcategories —
     * move to "Lainnya", and the subcategories are deleted with it (web behaviour).
     */
    fun deleteCategory(c: Category) = viewModelScope.launch(Dispatchers.IO) {
        val cats = db.categoryDao().allList()
        val lainnya = cats.firstOrNull { it.parentId == null && it.name.equals("Lainnya", ignoreCase = true) }
            ?: return@launch
        val children = cats.filter { it.parentId == c.id }
        val ids = listOf(c.id) + children.map { it.id }
        db.txnDao().reassignCategory(ids, lainnya.id)
        db.budgetDao().reassignCategory(ids, lainnya.id)
        db.recurringDao().reassignCategory(ids, lainnya.id)
        children.forEach { db.categoryDao().delete(it) }
        db.categoryDao().delete(c)
        AutoBackupScheduler.onDataChanged(getApplication())
        notify("Kategori dihapus · transaksi dipindah ke Lainnya")
    }

    /**
     * Apply the subcategory list staged in a parent category's editor: names that are
     * new are created under the parent (inheriting its icon/color/kind); subcategories
     * whose names were removed are deleted and their references move up to the parent.
     */
    fun syncSubcategories(parent: Category, desiredNames: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        val desired = desiredNames.map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        val current = db.categoryDao().childrenOf(parent.id)
        current.filter { cur -> desired.none { it.equals(cur.name, ignoreCase = true) } }
            .forEach { sub ->
                db.txnDao().reassignCategory(listOf(sub.id), parent.id)
                db.budgetDao().reassignCategory(listOf(sub.id), parent.id)
                db.recurringDao().reassignCategory(listOf(sub.id), parent.id)
                db.categoryDao().delete(sub)
            }
        val existing = current.map { it.name.lowercase() }.toSet()
        var order = (categories.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        desired.filter { it.lowercase() !in existing }.forEach { nm ->
            db.categoryDao().insert(
                Category(
                    name = nm, iconKey = parent.iconKey, colorHex = parent.colorHex,
                    kind = parent.kind, parentId = parent.id, sortOrder = order++,
                    isCustom = true
                )
            )
        }
        AutoBackupScheduler.onDataChanged(getApplication())
    }

    // ---------- accounts ----------
    fun saveAccount(a: Account, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.accountDao().update(a)
        else db.accountDao().insert(a.copy(sortOrder = (accounts.value.maxOfOrNull { it.sortOrder } ?: 0) + 1))
        notify("Akun disimpan")
    }

    fun deleteAccount(a: Account) = viewModelScope.launch(Dispatchers.IO) {
        db.accountDao().delete(a)
        notify("Akun dihapus")
    }

    fun balanceOf(accountId: Long): Flow<Long> = db.txnDao().balanceOf(accountId)

    // ---------- budgets ----------
    fun saveBudget(b: Budget, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.budgetDao().update(b) else db.budgetDao().insert(b)
        notify("Budget disimpan")
    }

    fun deleteBudget(b: Budget) = viewModelScope.launch(Dispatchers.IO) {
        db.budgetDao().delete(b)
        notify("Budget dihapus")
    }

    // ---------- recurring ----------
    fun saveRecurring(r: Recurring, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.recurringDao().update(r) else db.recurringDao().insert(r)
        notify("Transaksi berulang disimpan")
    }

    fun deleteRecurring(r: Recurring) = viewModelScope.launch(Dispatchers.IO) {
        db.recurringDao().delete(r)
        notify("Dihapus")
    }

    fun applyRecurringNow(r: Recurring) = viewModelScope.launch(Dispatchers.IO) {
        postRecurring(r)
        db.recurringDao().update(r.copy(nextDue = Dates.nextDue(r.nextDue, r.cadence)))
        notify("Diterapkan")
    }

    fun skipRecurring(r: Recurring) = viewModelScope.launch(Dispatchers.IO) {
        db.recurringDao().update(r.copy(nextDue = Dates.nextDue(r.nextDue, r.cadence)))
    }

    private suspend fun applyRecurringDue() {
        val now = System.currentTimeMillis()
        db.recurringDao().due(now).forEach { r ->
            var due = r.nextDue
            var guard = 0
            while (due <= now && guard++ < 36) {
                if (r.autoCreate) postRecurring(r.copy(nextDue = due))
                due = Dates.nextDue(due, r.cadence)
            }
            db.recurringDao().update(r.copy(nextDue = due))
        }
    }

    private suspend fun postRecurring(r: Recurring) {
        db.txnDao().insert(Txn(
            type = r.type, amountMinor = r.amountMinor, accountId = r.accountId,
            toAccountId = r.toAccountId, categoryId = r.categoryId, note = r.note,
            timestamp = r.nextDue, recurringId = r.id
        ))
    }

    // ---------- goals ----------
    fun saveGoal(g: SavingsGoal, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.goalDao().update(g) else db.goalDao().insert(g)
        notify(if (isEdit) "Target diperbarui" else "Target dibuat")
    }

    fun deleteGoal(g: SavingsGoal) = viewModelScope.launch(Dispatchers.IO) {
        db.goalDao().delete(g)
        notify("Target dihapus")
    }

    /** Add [amt] to a goal's saved amount, capped at target. */
    fun fillGoal(g: SavingsGoal, amt: Long) = viewModelScope.launch(Dispatchers.IO) {
        if (amt <= 0) return@launch
        db.goalDao().update(g.copy(savedMinor = minOf(g.targetMinor, g.savedMinor + amt)))
        notify("Tabungan bertambah ${MoneyFormat.formatCompact(amt, _currency.value)}")
    }

    // ---------- hutang & piutang (debts) ----------
    fun saveDebt(d: Debt, isEdit: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (isEdit) db.debtDao().update(d) else db.debtDao().insert(d)
        AutoBackupScheduler.onDataChanged(getApplication())
        notify(if (isEdit) "Catatan diperbarui" else "Catatan tersimpan")
    }

    /** Deleting a debt also deletes its payment history (web behaviour). */
    fun deleteDebt(d: Debt) = viewModelScope.launch(Dispatchers.IO) {
        db.debtDao().deletePaymentsOf(d.id)
        db.debtDao().delete(d)
        AutoBackupScheduler.onDataChanged(getApplication())
        notify("Catatan dihapus")
    }

    /** Record an installment; capped at the remaining amount. Full remainder settles the debt. */
    fun recordDebtPayment(debt: Debt, amount: Long, date: Long) = viewModelScope.launch(Dispatchers.IO) {
        val paid = db.debtDao().paymentsOf(debt.id).sumOf { it.amountMinor }
        val remain = maxOf(0L, debt.amountMinor - paid)
        val amt = minOf(amount, remain)
        if (amt <= 0) return@launch
        db.debtDao().insertPayment(DebtPayment(debtId = debt.id, amountMinor = amt, date = date))
        AutoBackupScheduler.onDataChanged(getApplication())
        notify(
            if (amt >= remain) "Lunas — pembayaran terakhir tercatat"
            else "Pembayaran tercatat · sisa ${MoneyFormat.format(remain - amt, _currency.value)}"
        )
    }

    /** "Tandai lunas": books one final payment for the exact remainder, dated today. */
    fun settleDebt(debt: Debt) = viewModelScope.launch(Dispatchers.IO) {
        val paid = db.debtDao().paymentsOf(debt.id).sumOf { it.amountMinor }
        val remain = maxOf(0L, debt.amountMinor - paid)
        if (remain > 0) {
            db.debtDao().insertPayment(
                DebtPayment(debtId = debt.id, amountMinor = remain, date = Dates.startOfDay(System.currentTimeMillis()))
            )
        }
        AutoBackupScheduler.onDataChanged(getApplication())
        notify("Ditandai lunas")
    }

    // ---------- settings ----------
    fun setCurrency(code: String) { prefs.baseCurrency = code; _currency.value = code }
    fun setDarkMode(m: Int) { prefs.darkMode = m; _darkMode.value = m }
    fun setMonthStart(d: Int) { prefs.monthStartDay = d; _monthStart.value = d }
    fun setProfileName(v: String) {
        val clean = v.trim().take(30)
        prefs.profileName = clean
        _profileName.value = clean
        notify(if (clean.isBlank()) "Nama dikosongkan" else "Nama disimpan")
    }

    // ---------- export / import / backup ----------
    fun exportCsv(onDone: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val path = CsvUtil.export(getApplication(), db, _currency.value)
            notify("CSV tersimpan: $path"); onDone(path)
        } catch (e: Exception) { notify("Gagal export: ${e.message}") }
    }

    fun importCsv(uri: Uri, onDone: (Int) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val n = CsvUtil.import(getApplication(), db, uri, _currency.value)
            notify("$n transaksi diimpor"); onDone(n)
        } catch (e: Exception) { notify("Gagal impor: ${e.message}") }
    }

    fun writeDownload(name: String, mime: String, text: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val path = BackupUtil.writeTextFile(getApplication(), name, mime, text)
            notify("Tersimpan: $path")
        } catch (e: Exception) { notify("Gagal menyimpan: ${e.message}") }
    }

    fun backup(onDone: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val path = BackupUtil.backup(getApplication(), db)
            notify("Backup tersimpan: $path"); onDone(path)
        } catch (e: Exception) { notify("Gagal backup: ${e.message}") }
    }

    fun restore(uri: Uri, onDone: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val n = BackupUtil.restore(getApplication(), db, uri)
            AutoBackupScheduler.onDataChanged(getApplication())
            notify("Dipulihkan: $n transaksi"); onDone()
        } catch (e: Exception) { notify("Gagal restore: ${e.message}") }
    }

    // ---------- Google Drive backup via SAF (system picker, no sign-in) ----------

    private fun driveDisplayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) { null }

    /** Write the JSON backup to [uri] (truncating). Returns true on success. */
    private suspend fun writeBackupTo(uri: Uri): Boolean {
        val json = BackupUtil.buildJson(getApplication(), db)
        val cr = getApplication<Application>().contentResolver
        cr.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
            ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
        return true
    }

    private fun refreshDriveInfo() {
        _driveInfo.value = DriveInfo(prefs.driveBackupUri, prefs.driveBackupName, prefs.driveBackupLast)
    }

    /** Called with the URI from the CreateDocument picker: persist access, remember it, write now. */
    fun driveBackupToNew(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val cr = getApplication<Application>().contentResolver
        try {
            cr.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { /* some providers don't grant persistable access; write still works */ }
        try {
            writeBackupTo(uri)
            prefs.driveBackupUri = uri.toString()
            prefs.driveBackupName = driveDisplayName(uri) ?: "saldo-backup.json"
            prefs.driveBackupLast = System.currentTimeMillis()
            refreshDriveInfo()
            notify("Backup Drive tersimpan ✓")
        } catch (e: Exception) {
            notify("Gagal menulis ke Drive: ${e.message}")
        }
    }

    /** Write to the remembered Drive document without reopening the picker.
     *  onResult(false) means the remembered location failed — the UI falls back to the picker. */
    fun driveBackupNow(onResult: (Boolean) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val stored = prefs.driveBackupUri
        if (stored.isBlank()) { onResult(false); return@launch }
        try {
            writeBackupTo(Uri.parse(stored))
            prefs.driveBackupLast = System.currentTimeMillis()
            refreshDriveInfo()
            notify("Backup Drive tersimpan ✓")
            onResult(true)
        } catch (e: Exception) {
            notify("Lokasi Drive tidak bisa diakses lagi — pilih lokasi baru")
            onResult(false)
        }
    }

    /** Forget the remembered Drive document and release our persisted access to it. */
    fun forgetDriveLocation() {
        val stored = prefs.driveBackupUri
        if (stored.isNotBlank()) {
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { /* already revoked/expired — nothing to release */ }
        }
        prefs.clearDriveBackup()
        // Forgetting the location also switches automatic backup off and disarms it.
        AutoBackupScheduler.cancelAll(getApplication())
        prefs.autoBackupEnabled = false
        prefs.lastAutoBackupAt = 0
        prefs.lastAutoBackupFailed = false
        refreshAutoBackup()
        refreshDriveInfo()
        notify("Lokasi Drive dilupakan")
    }
}
