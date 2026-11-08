package com.chitfund.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Date helpers. Dates are stored as UTC milliseconds, as the date picker gives them. */
object Dates {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun cal(ms: Long): Calendar = Calendar.getInstance(utc).apply { timeInMillis = ms }

    fun format(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).apply { timeZone = utc }.format(ms)

    fun monthYear(ms: Long): String =
        SimpleDateFormat("MMM yyyy", Locale.ENGLISH).apply { timeZone = utc }.format(ms)

    /** Same day-of-month, n months later (clamped for short months). */
    fun addMonths(ms: Long, n: Int): Long {
        val c = cal(ms)
        val day = c.get(Calendar.DAY_OF_MONTH)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, n)
        c.set(Calendar.DAY_OF_MONTH, minOf(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return c.timeInMillis
    }

    /** Calendar months from [startMs] to today (0 in the start month, negative before it). */
    fun monthsSince(startMs: Long): Int {
        val s = cal(startMs)
        val now = Calendar.getInstance()
        return (now.get(Calendar.YEAR) - s.get(Calendar.YEAR)) * 12 +
            (now.get(Calendar.MONTH) - s.get(Calendar.MONTH))
    }

    /** Months that have started so far, from 0 up to [total]. */
    fun elapsed(c: Chit): Int {
        val s = c.startMillis ?: return 0
        return (monthsSince(s) + 1).coerceIn(0, c.members)
    }

    /** The month shown on the Home slide: the current one, at least 1. */
    fun currentMonth(c: Chit): Int = elapsed(c).coerceAtLeast(1)

    fun monthDate(c: Chit, month: Int): Long? = c.startMillis?.let { addMonths(it, month - 1) }

    /** Running month: the last month that has a bid entered, else the month by date. */
    fun running(c: Chit, bidMonths: Collection<Int>): Int =
        (bidMonths.maxOrNull() ?: currentMonth(c)).coerceIn(1, c.members)
}
