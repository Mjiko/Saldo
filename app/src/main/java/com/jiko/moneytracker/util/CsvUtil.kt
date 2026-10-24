package com.jiko.moneytracker.util

import android.content.Context
import android.net.Uri
import com.jiko.moneytracker.data.MoneyDatabase
import com.jiko.moneytracker.data.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvUtil {
    private const val HEADER = "date,type,amount,account,category,note,tags"

    suspend fun export(context: Context, db: MoneyDatabase, currency: String): String =
        withContext(Dispatchers.IO) {
            val txns = db.txnDao().allList()
            val accs = db.accountDao().allList().associateBy { it.id }
            val cats = db.categoryDao().allList().associateBy { it.id }
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val sb = StringBuilder(HEADER).append('\n')
            txns.forEach { t ->
                sb.append(df.format(Date(t.timestamp))).append(',')
                sb.append(t.type).append(',')
                sb.append(t.amountMinor).append(',')
                sb.append(esc(accs[t.accountId]?.name ?: "")).append(',')
                sb.append(esc(t.categoryId?.let { cats[it]?.name } ?: "")).append(',')
                sb.append(esc(t.note)).append(',')
                sb.append(esc(t.tags)).append('\n')
            }
            BackupUtil.writeTextFile(context, "saldo-transactions-${System.currentTimeMillis()}.csv", "text/csv", sb.toString())
        }

    /** Returns number of imported rows. Matches accounts/categories by name (case-insensitive); creates missing categories. */
    suspend fun import(context: Context, db: MoneyDatabase, uri: Uri, currency: String): Int =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                ?: throw IllegalArgumentException("Tidak bisa membaca file")
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return@withContext 0
            val start = if (lines.first().trim().startsWith("date", ignoreCase = true)) 1 else 0
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val accs = db.accountDao().allList()
            val cats = db.categoryDao().allList().toMutableList()
            var count = 0
            for (i in start until lines.size) {
                val cols = splitCsv(lines[i])
                if (cols.size < 6) continue
                try {
                    val ts = df.parse(cols[0].trim())?.time ?: System.currentTimeMillis()
                    val type = cols[1].trim().uppercase().let { if (it in setOf("EXPENSE", "INCOME", "TRANSFER")) it else "EXPENSE" }
                    val amount = cols[2].trim().toLongOrNull() ?: continue
                    val accName = cols[3].trim()
                    val catName = cols[4].trim()
                    val note = cols.getOrElse(5) { "" }.trim()
                    val tags = cols.getOrElse(6) { "" }.trim()
                    val accountId = accs.firstOrNull { it.name.equals(accName, true) }?.id
                        ?: accs.firstOrNull()?.id ?: continue
                    val categoryId = if (catName.isBlank()) null else
                        cats.firstOrNull { it.name.equals(catName, true) }?.id
                            ?: db.categoryDao().insert(
                                com.jiko.moneytracker.data.Category(
                                    name = catName, kind = if (type == "INCOME") "INCOME" else "EXPENSE",
                                    sortOrder = cats.size)
                            ).also { id ->
                                cats.add(db.categoryDao().byId(id)!!)
                            }
                    db.txnDao().insert(Txn(type = type, amountMinor = amount, accountId = accountId,
                        categoryId = categoryId, note = note, tags = tags, timestamp = ts))
                    count++
                } catch (_: Exception) { /* skip bad row */ }
            }
            count
        }

    private fun esc(s: String): String {
        val v = s.replace("\"", "\"\"")
        return if (v.contains(',') || v.contains('"') || v.contains('\n')) "\"$v\"" else v
    }

    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQ && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ }
                    else inQ = !inQ
                }
                c == ',' && !inQ -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
