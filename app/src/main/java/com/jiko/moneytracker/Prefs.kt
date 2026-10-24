package com.jiko.moneytracker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("saldo_prefs", Context.MODE_PRIVATE)

    var baseCurrency: String
        get() = sp.getString("currency", "IDR") ?: "IDR"
        set(v) = sp.edit { putString("currency", v) }

    /** 0 = system, 1 = light, 2 = dark */
    var darkMode: Int
        get() = sp.getInt("dark_mode", 0)
        set(v) = sp.edit { putInt("dark_mode", v) }

    var monthStartDay: Int
        get() = sp.getInt("month_start", 1).coerceIn(1, 28)
        set(v) = sp.edit { putInt("month_start", v.coerceIn(1, 28)) }

    var lockEnabled: Boolean
        get() = sp.getBoolean("lock", false)
        set(v) = sp.edit { putBoolean("lock", v) }

    /** Hide balances on Home (eye toggle). */
    var hideBalance: Boolean
        get() = sp.getBoolean("hide_balance", false)
        set(v) = sp.edit { putBoolean("hide_balance", v) }

    /** User-editable profile name shown in the Beranda greeting. */
    var profileName: String
        get() = sp.getString("profile_name", "") ?: ""
        set(v) = sp.edit { putString("profile_name", v.trim().take(30)) }

    /** Greeting name with fallback when the user hasn't set one. */
    fun displayName(): String = profileName.trim().ifBlank { "Jiko" }

    // ---- Google Drive backup (SAF remembered document) ----

    /** Content URI of the Drive document chosen via the system picker. */
    var driveBackupUri: String
        get() = sp.getString("drive_backup_uri", "") ?: ""
        set(v) = sp.edit { putString("drive_backup_uri", v) }

    /** Display name of the remembered Drive document. */
    var driveBackupName: String
        get() = sp.getString("drive_backup_name", "") ?: ""
        set(v) = sp.edit { putString("drive_backup_name", v) }

    /** Epoch millis of the last successful Drive backup (0 = never). */
    var driveBackupLast: Long
        get() = sp.getLong("drive_backup_last", 0L)
        set(v) = sp.edit { putLong("drive_backup_last", v) }

    fun clearDriveBackup() = sp.edit {
        remove("drive_backup_uri"); remove("drive_backup_name"); remove("drive_backup_last")
    }

    // ---- Automatic Drive backup (extends the manual backup above) ----

    /** Whether automatic backup to the remembered Drive document is switched on. */
    var autoBackupEnabled: Boolean
        get() = sp.getBoolean("drive_auto", false)
        set(v) = sp.edit { putBoolean("drive_auto", v) }

    /** Frequency code: "ON_CHANGE" | "DAILY" | "WEEKLY" (see util.AutoBackupFreq). */
    var autoBackupFreq: String
        get() = sp.getString("drive_auto_freq", "ON_CHANGE") ?: "ON_CHANGE"
        set(v) = sp.edit { putString("drive_auto_freq", v) }

    /** Epoch millis of the last successful AUTOMATIC backup (0 = never). */
    var lastAutoBackupAt: Long
        get() = sp.getLong("drive_auto_last", 0L)
        set(v) = sp.edit { putLong("drive_auto_last", v) }

    /** True when the most recent automatic backup attempt failed. */
    var lastAutoBackupFailed: Boolean
        get() = sp.getBoolean("drive_auto_failed", false)
        set(v) = sp.edit { putBoolean("drive_auto_failed", v) }

    var biometricEnabled: Boolean
        get() = sp.getBoolean("bio", false)
        set(v) = sp.edit { putBoolean("bio", v) }

    fun setPin(pin: String) {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(("saldo:$pin").toByteArray()).joinToString("") { "%02x".format(it) }
        sp.edit { putString("pin_hash", hash) }
    }

    fun hasPin(): Boolean = sp.contains("pin_hash")

    fun verifyPin(pin: String): Boolean {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(("saldo:$pin").toByteArray()).joinToString("") { "%02x".format(it) }
        return sp.getString("pin_hash", "") == hash
    }

    fun clearPin() = sp.edit { remove("pin_hash") }

    var lastAccountId: Long
        get() = sp.getLong("last_acc", -1)
        set(v) = sp.edit { putLong("last_acc", v) }

    /** note (lowercased) -> categoryId, for smart defaults */
    fun rememberCategory(note: String, categoryId: Long) {
        if (note.isBlank()) return
        sp.edit { putLong("cat_note_${note.lowercase().take(40)}", categoryId) }
    }

    fun categoryForNote(note: String): Long =
        sp.getLong("cat_note_${note.lowercase().take(40)}", -1)

    /** exact-amount repeat suggestions: "note|amount" -> count */
    fun rememberRepeat(note: String, amountMinor: Long) {
        if (note.isBlank() || amountMinor <= 0) return
        val k = "rep_${note.lowercase().take(30)}_$amountMinor"
        sp.edit { putInt(k, sp.getInt(k, 0) + 1) }
    }

    fun topRepeats(limit: Int = 4): List<Pair<String, Long>> {
        return sp.all.mapNotNull { (k, v) ->
            if (k.startsWith("rep_") && (v as? Int ?: 0) >= 2) {
                val rest = k.removePrefix("rep_")
                val amt = rest.substringAfterLast('_').toLongOrNull() ?: return@mapNotNull null
                val note = rest.substringBeforeLast('_')
                Triple(note, amt, v as Int)
            } else null
        }.sortedByDescending { it.third }.take(limit).map { it.first to it.second }
    }
}
