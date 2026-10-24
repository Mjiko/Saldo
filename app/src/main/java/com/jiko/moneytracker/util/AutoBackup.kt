package com.jiko.moneytracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jiko.moneytracker.MainActivity
import com.jiko.moneytracker.Prefs
import com.jiko.moneytracker.data.MoneyDatabase
import java.util.concurrent.TimeUnit

/** How often Saldo backs itself up to the remembered Drive document. */
enum class AutoBackupFreq {
    ON_CHANGE, DAILY, WEEKLY;

    companion object {
        fun fromStored(v: String): AutoBackupFreq = values().firstOrNull { it.name == v } ?: ON_CHANGE
    }
}

/**
 * Performs one automatic Drive backup: builds the JSON via the same
 * [BackupUtil.buildJson] the manual backup uses and truncates-writes it to the
 * remembered SAF document. Runs on WorkManager's background thread, app open or not.
 * A failure never retries in a loop — it flags Prefs and posts one alert instead;
 * the next data change / scheduled period tries again.
 */
class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        // The feature may have been switched off after this work was enqueued.
        if (!prefs.autoBackupEnabled) return Result.success()
        val uriStr = prefs.driveBackupUri
        if (uriStr.isBlank()) {
            prefs.lastAutoBackupFailed = true
            AutoBackupNotifier.notifyFailure(applicationContext)
            return Result.success()
        }
        return try {
            val json = BackupUtil.buildJson(applicationContext, MoneyDatabase.get(applicationContext))
            val cr = applicationContext.contentResolver
            cr.openOutputStream(Uri.parse(uriStr), "wt")?.use { it.write(json.toByteArray()) }
                ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
            val now = System.currentTimeMillis()
            prefs.lastAutoBackupAt = now
            // An automatic backup IS a backup: the manual "Cadangan terakhir" follows it.
            prefs.driveBackupLast = now
            prefs.lastAutoBackupFailed = false
            Result.success()
        } catch (e: Exception) {
            prefs.lastAutoBackupFailed = true
            AutoBackupNotifier.notifyFailure(applicationContext)
            Result.success()
        }
    }
}

/** Owns all WorkManager wiring for automatic backup: unique names, debounce, (re)scheduling. */
object AutoBackupScheduler {
    private const val PERIODIC = "saldo_auto_backup_periodic"
    private const val ON_CHANGE_WORK = "saldo_auto_backup_on_change"
    private const val ON_CHANGE_DELAY_SECONDS = 30L

    /**
     * Central hook called from the main data-write paths (transactions, debts/payments,
     * categories). In ON_CHANGE mode it (re)arms a one-time backup 30 s out — REPLACE
     * keeps pushing the deadline while the user is actively editing, so a burst of
     * changes produces a single backup. No-op in every other state.
     */
    fun onDataChanged(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.autoBackupEnabled) return
        if (AutoBackupFreq.fromStored(prefs.autoBackupFreq) != AutoBackupFreq.ON_CHANGE) return
        if (prefs.driveBackupUri.isBlank()) return
        try {
            val req = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInitialDelay(ON_CHANGE_DELAY_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ON_CHANGE_WORK, ExistingWorkPolicy.REPLACE, req)
        } catch (e: Exception) { /* WorkManager not initialised yet — next change re-arms */ }
    }

    /** Cancel everything, then re-arm periodic work for DAILY/WEEKLY if the feature is on. */
    fun reschedule(context: Context) {
        cancelAll(context)
        val prefs = Prefs(context)
        if (!prefs.autoBackupEnabled || prefs.driveBackupUri.isBlank()) return
        val days = when (AutoBackupFreq.fromStored(prefs.autoBackupFreq)) {
            AutoBackupFreq.DAILY -> 1L
            AutoBackupFreq.WEEKLY -> 7L
            AutoBackupFreq.ON_CHANGE -> return
        }
        try {
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, req
            )
        } catch (e: Exception) { /* same as above */ }
    }

    fun cancelAll(context: Context) {
        try {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(ON_CHANGE_WORK)
        } catch (e: Exception) { /* nothing scheduled / not initialised */ }
    }
}

/** The single failure alert for automatic backup (fixed id — repeats replace, never stack). */
object AutoBackupNotifier {
    private const val CHANNEL_ID = "saldo_auto_backup"
    private const val NOTIF_ID = 4717

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Backup otomatis", NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Pemberitahuan bila backup otomatis ke Google Drive gagal"
                }
            )
        }
    }

    /** Posts the failure alert; silently skipped when notifications aren't granted (API 33+). */
    fun notifyFailure(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Backup otomatis gagal")
            .setContentText("Buka Saldo untuk memeriksa lokasi Drive")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        } catch (e: SecurityException) { /* permission revoked between check and post */ }
    }
}
