package com.jiko.moneytracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Dates {
    private val id = Locale("in", "ID")

    fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun endOfDay(ts: Long): Long = startOfDay(ts) + 86_400_000L - 1

    /** Budget period [from, to] for the month containing [ts], with custom start day (1..28). */
    fun budgetPeriod(ts: Long, startDay: Int): Pair<Long, Long> {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val day = c.get(Calendar.DAY_OF_MONTH)
        if (day < startDay.coerceIn(1, 28)) c.add(Calendar.MONTH, -1)
        c.set(Calendar.DAY_OF_MONTH, startDay.coerceIn(1, 28))
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return from to (c.timeInMillis - 1)
    }

    fun monthRange(year: Int, month0: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(year, month0, 1, 0, 0, 0); c.set(Calendar.MILLISECOND, 0)
        val from = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return from to (c.timeInMillis - 1)
    }

    fun monthLabel(ts: Long): String = SimpleDateFormat("MMMM yyyy", id).format(ts)
    fun monthShort(ts: Long): String = SimpleDateFormat("MMM", id).format(ts)
    fun monthName(ts: Long): String = SimpleDateFormat("MMMM", id).format(ts)
    fun dayLabel(ts: Long): String = SimpleDateFormat("d MMM yyyy", id).format(ts)
    /** "25 Okt" — web dfmtShort style. */
    fun dayMonth(ts: Long): String = SimpleDateFormat("d MMM", id).format(ts)
    /** "31 Oktober 2026". */
    fun fullDate(ts: Long): String = SimpleDateFormat("d MMMM yyyy", id).format(ts)

    /** "Sen, 12 Okt 2026" — web dfmt style. */
    fun dayShort(ts: Long): String {
        val today = startOfDay(System.currentTimeMillis())
        val d = startOfDay(ts)
        return when {
            d == today -> "Hari ini"
            d == today - 86_400_000L -> "Kemarin"
            else -> SimpleDateFormat("E, d MMM yyyy", id).format(ts)
        }
    }
    fun dayHeader(ts: Long): String {
        val today = startOfDay(System.currentTimeMillis())
        val d = startOfDay(ts)
        return when {
            d == today -> "Hari ini"
            d == today - 86_400_000L -> "Kemarin"
            else -> SimpleDateFormat("EEEE, d MMM", id).format(ts)
        }
    }

    fun addMonths(ts: Long, n: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        c.add(Calendar.MONTH, n)
        return c.timeInMillis
    }

    fun nextDue(from: Long, cadence: String): Long {
        val c = Calendar.getInstance().apply { timeInMillis = from }
        when (cadence) {
            "DAILY" -> c.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> c.add(Calendar.WEEK_OF_YEAR, 1)
            "YEARLY" -> c.add(Calendar.YEAR, 1)
            else -> c.add(Calendar.MONTH, 1)
        }
        return c.timeInMillis
    }

    fun monthKey(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}"
    }
}
