package com.jiko.moneytracker.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jiko.moneytracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BackupUtil {

    /** Full backup payload as JSON text — shared by the local Download backup and the Drive (SAF) backup. */
    suspend fun buildJson(context: Context, db: MoneyDatabase): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val txns = JSONArray()
        db.txnDao().allList().forEach {
            txns.put(JSONObject()
                .put("type", it.type).put("amountMinor", it.amountMinor)
                .put("accountId", it.accountId).put("toAccountId", it.toAccountId ?: -1)
                .put("categoryId", it.categoryId ?: -1).put("note", it.note)
                .put("tags", it.tags).put("timestamp", it.timestamp)
                .put("receiptPath", it.receiptPath ?: ""))
        }
        val cats = JSONArray()
        db.categoryDao().allList().forEach {
            cats.put(JSONObject().put("id", it.id).put("name", it.name).put("iconKey", it.iconKey)
                .put("colorHex", it.colorHex).put("kind", it.kind)
                .put("parentId", it.parentId ?: -1).put("sortOrder", it.sortOrder)
                .put("isCustom", it.isCustom))
        }
        val accs = JSONArray()
        db.accountDao().allList().forEach {
            accs.put(JSONObject().put("name", it.name).put("currency", it.currency)
                .put("iconKey", it.iconKey).put("colorHex", it.colorHex)
                .put("sortOrder", it.sortOrder))
        }
        val budgets = JSONArray()
        db.budgetDao().allList().forEach {
            budgets.put(JSONObject().put("categoryId", it.categoryId ?: -1)
                .put("amountMinor", it.amountMinor).put("monthStartDay", it.monthStartDay)
                .put("alertPct", it.alertPct))
        }
        val recs = JSONArray()
        db.recurringDao().allList().forEach {
            recs.put(JSONObject().put("type", it.type).put("amountMinor", it.amountMinor)
                .put("categoryId", it.categoryId ?: -1).put("accountId", it.accountId)
                .put("toAccountId", it.toAccountId ?: -1).put("note", it.note)
                .put("cadence", it.cadence).put("nextDue", it.nextDue)
                .put("autoCreate", it.autoCreate).put("active", it.active))
        }
        val goalArr = JSONArray()
        db.goalDao().allList().forEach {
            goalArr.put(JSONObject().put("name", it.name)
                .put("targetMinor", it.targetMinor).put("savedMinor", it.savedMinor)
                .put("colorHex", it.colorHex).put("accountId", it.accountId)
                .put("deadline", it.deadline))
        }
        val debtArr = JSONArray()
        val paymentsByDebt = db.debtDao().allPaymentsList().groupBy { it.debtId }
        db.debtDao().allList().forEach { d ->
            val pays = JSONArray()
            (paymentsByDebt[d.id] ?: emptyList()).forEach { p ->
                pays.put(JSONObject().put("amountMinor", p.amountMinor).put("date", p.date))
            }
            debtArr.put(JSONObject().put("side", d.side).put("person", d.person)
                .put("amountMinor", d.amountMinor).put("note", d.note)
                .put("date", d.date).put("due", d.due ?: -1)
                .put("payments", pays))
        }
        root.put("transactions", txns)
        root.put("categories", cats)
        root.put("accounts", accs)
        root.put("budgets", budgets)
        root.put("recurring", recs)
        root.put("goals", goalArr)
        root.put("debts", debtArr)
        root.toString(1)
    }

    suspend fun backup(context: Context, db: MoneyDatabase): String = withContext(Dispatchers.IO) {
        val name = "saldo-backup-${System.currentTimeMillis()}.json"
        writeTextFile(context, name, "application/json", buildJson(context, db))
    }

    suspend fun restore(context: Context, db: MoneyDatabase, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IllegalArgumentException("Tidak bisa membaca file")
        val root = JSONObject(text)
        var count = 0
        // Wipe + reinsert (ids remapped: keep simple id mapping per table)
        db.clearAllTables()
        val catIdMap = mutableMapOf<Long, Long>()
        val accIdMap = mutableMapOf<Long, Long>()
        // Categories first (fresh ids). Backups from v1.6+ carry each category's real
        // "id", so references resolve exactly; older backups have no "id" and keep the
        // legacy position-based mapping (their subcategory parents were dropped by the
        // old restore, so they are re-linked best-effort via id-1 = position).
        val catArr = root.optJSONArray("categories") ?: JSONArray()
        val catHasIds = catArr.length() > 0 && catArr.getJSONObject(0).has("id")
        val insertedCatIds = mutableListOf<Long>()
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            val newId = db.categoryDao().insert(Category(
                name = o.getString("name"), iconKey = o.optString("iconKey", "more"),
                colorHex = o.optString("colorHex", "#9E9E9E"), kind = o.optString("kind", "EXPENSE"),
                parentId = null, sortOrder = o.optInt("sortOrder", i),
                isCustom = o.optBoolean("isCustom", false)))
            insertedCatIds.add(newId)
            catIdMap[if (catHasIds) o.optLong("id", i.toLong()) else i.toLong()] = newId
        }
        // Second pass: link subcategories to their (remapped) parents.
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            val parentRef = o.optLong("parentId", -1)
            if (parentRef >= 0) {
                val parentNewId = if (catHasIds) catIdMap[parentRef] else catIdMap[parentRef - 1]
                if (parentNewId != null) {
                    val self = db.categoryDao().byId(insertedCatIds[i])
                    if (self != null) db.categoryDao().update(self.copy(parentId = parentNewId))
                }
            }
        }
        val accArr = root.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accArr.length()) {
            val o = accArr.getJSONObject(i)
            val newId = db.accountDao().insert(Account(
                name = o.getString("name"), currency = o.optString("currency", "IDR"),
                iconKey = o.optString("iconKey", "wallet"),
                colorHex = o.optString("colorHex", "#FF5722"),
                sortOrder = o.optInt("sortOrder", i)))
            accIdMap[i.toLong()] = newId
        }

        val txnArr = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txnArr.length()) {
            val o = txnArr.getJSONObject(i)
            val accIdx = o.optLong("accountId", 0)
            val toIdx = o.optLong("toAccountId", -1)
            val catIdx = o.optLong("categoryId", -1)
            db.txnDao().insert(Txn(
                type = o.optString("type", "EXPENSE"),
                amountMinor = o.optLong("amountMinor", 0),
                accountId = accIdMap[accIdx] ?: accIdMap.values.firstOrNull() ?: 0,
                toAccountId = if (toIdx >= 0) accIdMap[toIdx] else null,
                categoryId = if (catIdx >= 0) catIdMap[catIdx] else null,
                note = o.optString("note", ""), tags = o.optString("tags", ""),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                receiptPath = o.optString("receiptPath", "").ifBlank { null }))
            count++
        }
        val bArr = root.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until bArr.length()) {
            val o = bArr.getJSONObject(i)
            val catIdx = o.optLong("categoryId", -1)
            db.budgetDao().insert(Budget(
                categoryId = if (catIdx >= 0) catIdMap[catIdx] else null,
                amountMinor = o.optLong("amountMinor", 0),
                monthStartDay = o.optInt("monthStartDay", 1),
                alertPct = o.optInt("alertPct", 90)))
        }
        val rArr = root.optJSONArray("recurring") ?: JSONArray()
        for (i in 0 until rArr.length()) {
            val o = rArr.getJSONObject(i)
            val accIdx = o.optLong("accountId", 0)
            val toIdx = o.optLong("toAccountId", -1)
            val catIdx = o.optLong("categoryId", -1)
            db.recurringDao().insert(Recurring(
                type = o.optString("type", "EXPENSE"),
                amountMinor = o.optLong("amountMinor", 0),
                categoryId = if (catIdx >= 0) catIdMap[catIdx] else null,
                accountId = accIdMap[accIdx] ?: accIdMap.values.firstOrNull() ?: 0,
                toAccountId = if (toIdx >= 0) accIdMap[toIdx] else null,
                note = o.optString("note", ""),
                cadence = o.optString("cadence", "MONTHLY"),
                nextDue = o.optLong("nextDue", System.currentTimeMillis()),
                autoCreate = o.optBoolean("autoCreate", true),
                active = o.optBoolean("active", true)))
        }
        val gArr = root.optJSONArray("goals") ?: JSONArray()
        for (i in 0 until gArr.length()) {
            val o = gArr.getJSONObject(i)
            val accIdx = o.optLong("accountId", 0)
            db.goalDao().insert(SavingsGoal(
                name = o.optString("name", ""),
                targetMinor = o.optLong("targetMinor", 0),
                savedMinor = o.optLong("savedMinor", 0),
                colorHex = o.optString("colorHex", "#FF5722"),
                accountId = accIdMap[accIdx] ?: accIdMap.values.firstOrNull() ?: 0,
                deadline = o.optLong("deadline", System.currentTimeMillis())))
        }
        // Debts + payments: absent in backups written before v1.5 — skip then.
        val dArr = root.optJSONArray("debts") ?: JSONArray()
        for (i in 0 until dArr.length()) {
            val o = dArr.getJSONObject(i)
            val newDebtId = db.debtDao().insert(Debt(
                side = o.optString("side", "PIUTANG"),
                person = o.optString("person", ""),
                amountMinor = o.optLong("amountMinor", 0),
                note = o.optString("note", ""),
                date = o.optLong("date", System.currentTimeMillis()),
                due = o.optLong("due", -1).takeIf { it >= 0 }))
            val pArr = o.optJSONArray("payments") ?: JSONArray()
            for (j in 0 until pArr.length()) {
                val p = pArr.getJSONObject(j)
                db.debtDao().insertPayment(DebtPayment(
                    debtId = newDebtId,
                    amountMinor = p.optLong("amountMinor", 0),
                    date = p.optLong("date", System.currentTimeMillis())))
            }
        }
        count
    }

    suspend fun writeTextFile(context: Context, name: String, mime: String, text: String): String =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Saldo")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Gagal membuat file")
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                "Download/Saldo/$name"
            } else {
                val dir = File(context.getExternalFilesDir(null), "Saldo").apply { mkdirs() }
                File(dir, name).writeText(text)
                dir.absolutePath + "/" + name
            }
        }
}

// One-shot (non-Flow) helpers used by backup/restore & CSV.
suspend fun TxnDao.allOnce(): List<Txn> = allList()
