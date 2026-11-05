package com.chitfund.app

import kotlin.math.abs
import kotlin.math.pow

data class Analysis(
    val winMonth: Int,
    val totalPaid: Double,
    val prize: Double,
    val net: Double,
    /** Effective yearly rate from the cash flows (always >= 0 when found). */
    val annualPercent: Double?,
    /** Positive = interest earned, negative = interest paid (cost of taking the money early). */
    val signedAnnualPercent: Double?
)

object ChitMath {
    fun chitValue(c: Chit) = c.members * c.monthlyAmount

    /** Commission is a % of the chit value, deducted from the winner's take-home. */
    fun commission(value: Double, pct: Double) = value * pct / 100.0

    /** Each member pays monthly amount minus (bid / members). */
    fun installment(monthly: Double, members: Int, bid: Double) = monthly - bid / members

    /** Month's saving as a percent of the monthly amount: (monthly - installment) / monthly. */
    fun gainPercent(monthly: Double, members: Int, bid: Double) =
        if (monthly > 0) bid / members / monthly * 100.0 else 0.0

    /** What the month's winner takes home. */
    fun prize(value: Double, bid: Double, pct: Double) = value - bid - commission(value, pct)

    /** Bid for a month; months with no entry reuse the last known earlier bid (estimate). */
    fun bidFor(bids: Map<Int, Double>, month: Int): Double {
        for (m in month downTo 1) bids[m]?.let { return it }
        return 0.0
    }

    fun analyse(c: Chit, bids: Map<Int, Double>, win: Int): Analysis {
        val n = c.members
        val value = chitValue(c)
        val flows = DoubleArray(n) { i ->
            val m = i + 1
            val b = bidFor(bids, m)
            var f = -installment(c.monthlyAmount, n, b)
            if (m == win) f += prize(value, b, c.commissionPercent)
            f
        }
        val paid = (1..n).sumOf { installment(c.monthlyAmount, n, bidFor(bids, it)) }
        val prize = prize(value, bidFor(bids, win), c.commissionPercent)
        val net = prize - paid
        val monthly = irr(flows)
        val annual = monthly?.let { ((1 + it).pow(12.0) - 1) * 100 }
        val signed = annual?.let { if (net >= 0) abs(it) else -abs(it) }
        return Analysis(win, paid, prize, net, annual, signed)
    }

    /**
     * Monthly IRR. Scans outward from 0% in small steps for the first sign change of the NPV
     * (a mid-way winner can have NPV of the same sign at both ends), then bisects. Returns the
     * root closest to 0 within [-50%, +100%] per month, or null if none exists.
     */
    private fun irr(f: DoubleArray): Double? {
        fun npv(r: Double) = f.indices.sumOf { f[it] / (1 + r).pow((it + 1).toDouble()) }

        fun root(step: Double, limit: Double): Double? {
            var a = 0.0
            var fa = npv(a)
            if (fa == 0.0) return 0.0
            var r = step
            while (abs(r) <= abs(limit) + 1e-12) {
                val fr = npv(r)
                if (fa * fr <= 0) {
                    var lo = a
                    var hi = r
                    repeat(100) {
                        val mid = (lo + hi) / 2
                        if (npv(lo) * npv(mid) <= 0) hi = mid else lo = mid
                    }
                    return (lo + hi) / 2
                }
                a = r
                fa = fr
                r += step
            }
            return null
        }

        val up = root(0.0005, 1.0)
        val down = root(-0.0005, -0.5)
        return listOfNotNull(up, down).minByOrNull { abs(it) }
    }
}
