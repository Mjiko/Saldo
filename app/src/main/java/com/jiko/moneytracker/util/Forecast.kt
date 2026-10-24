package com.jiko.moneytracker.util

import com.jiko.moneytracker.data.Recurring
import com.jiko.moneytracker.data.Txn
import java.util.Calendar

/** One projected day: balance at end of that day. */
data class ForecastPoint(val dayStartTs: Long, val balance: Long)

/** A scheduled occurrence with the running projected balance for its day. */
data class ForecastEvent(
    val dayStartTs: Long,
    val recurring: Recurring,
    val balanceAfter: Long
)

data class ForecastResult(
    val avgDailySpend: Long,
    val points: List<ForecastPoint>,
    val paydayTs: Long?, // null when there is no income schedule at all
    val daysToPayday: Int,
    val safeDaily: Long,
    val atPayday: Long,
    val atMonthEnd: Long,
    val atHorizonEnd: Long,
    val events: List<ForecastEvent>,
    val billsBeforePayday: Long,
    val monthEndTs: Long,
    val horizonEndTs: Long
)

/**
 * Balance projection — mirrors the web version's forecastSummary():
 * today + recurring schedules (income adds, expenses subtract), minus the average
 * daily free spend (expenses outside scheduled categories, this month so far).
 */
object Forecast {
    private const val DAY = 86_400_000L

    fun compute(
        txns: List<Txn>,
        recurrings: List<Recurring>,
        totalBalance: Long,
        now: Long = System.currentTimeMillis()
    ): ForecastResult {
        val today = Dates.startOfDay(now)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val elapsedDays = cal.get(Calendar.DAY_OF_MONTH) // 1..N, days so far this month

        // Month boundaries: start of this month .. start of last day of next month.
        val monthStart = Dates.startOfDay(
            Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        )
        val monthEnd = Dates.startOfDay(
            Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, 0, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        )
        val horizonEnd = Dates.startOfDay(
            Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 2, 0, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        )
        val days = ((horizonEnd - today) / DAY).toInt().coerceAtLeast(1)

        val active = recurrings.filter { it.active }

        // Average daily free spend: expenses this month outside scheduled categories.
        val schedCats = active.filter { it.type == "EXPENSE" }.mapNotNull { it.categoryId }.toSet()
        val freeExp = txns.filter {
            it.type == "EXPENSE" && it.timestamp in monthStart..now && it.categoryId !in schedCats
        }.sumOf { it.amountMinor }
        val avg = (freeExp + elapsedDays / 2) / elapsedDays

        // Expand recurring occurrences in [today, horizonEnd], then sort by date.
        data class Ev(val day: Long, val r: Recurring)
        val evs = mutableListOf<Ev>()
        for (r in active) {
            var d = Dates.startOfDay(r.nextDue)
            var guard = 0
            while (d < today && guard++ < 500) d = Dates.startOfDay(Dates.nextDue(d, r.cadence))
            while (d <= horizonEnd && guard++ < 1000) {
                if (d >= today) evs.add(Ev(d, r))
                d = Dates.startOfDay(Dates.nextDue(d, r.cadence))
            }
        }
        evs.sortBy { it.day }
        val byDate = evs.groupBy { it.day }

        // Daily projection walk — web applies only future days; today's balance is the
        // actual current balance (scheduled items due today are already accounted).
        val points = mutableListOf(ForecastPoint(today, totalBalance))
        var bal = totalBalance
        for (i in 1..days) {
            val d = today + i * DAY
            bal -= avg
            byDate[d]?.forEach { e ->
                bal += if (e.r.type == "INCOME") e.r.amountMinor else -e.r.amountMinor
            }
            points.add(ForecastPoint(d, bal))
        }
        fun at(ds: Long): Long = points.firstOrNull { it.dayStartTs == ds }?.balance ?: points.last().balance

        // Next payday: first active income schedule, stepped forward to >= today.
        var payday: Long? = null
        val incRec = active.filter { it.type == "INCOME" }.minByOrNull { it.nextDue }
        if (incRec != null) {
            var d = Dates.startOfDay(incRec.nextDue)
            var guard = 0
            while (d < today && guard++ < 500) d = Dates.startOfDay(Dates.nextDue(d, incRec.cadence))
            payday = d
        }
        val payTs = payday ?: monthEnd
        val daysToPay = ((payTs - today) / DAY).toInt().coerceAtLeast(1)
        val billsBefore = evs.filter { it.day >= today && it.day < payTs && it.r.type == "EXPENSE" }
            .sumOf { it.r.amountMinor }
        val safeDaily = Math.floorDiv(totalBalance - billsBefore, daysToPay.toLong()).coerceAtLeast(0L)

        val events = evs.take(500).map { ForecastEvent(it.day, it.r, at(it.day)) }

        return ForecastResult(
            avgDailySpend = avg,
            points = points,
            paydayTs = payday,
            daysToPayday = daysToPay,
            safeDaily = safeDaily,
            atPayday = at(payTs),
            atMonthEnd = at(monthEnd),
            atHorizonEnd = points.last().balance,
            events = events,
            billsBeforePayday = billsBefore,
            monthEndTs = monthEnd,
            horizonEndTs = horizonEnd
        )
    }
}
