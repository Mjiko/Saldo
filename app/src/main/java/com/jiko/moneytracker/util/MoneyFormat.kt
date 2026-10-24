package com.jiko.moneytracker.util

import java.text.NumberFormat
import java.util.Locale

object MoneyFormat {
    private val idLocale = Locale("in", "ID")

    val CURRENCIES = listOf("IDR", "USD", "SGD", "MYR", "EUR", "JPY", "AUD", "GBP")

    fun symbol(code: String): String = when (code) {
        "IDR" -> "Rp"
        "USD" -> "$"
        "SGD" -> "S$"
        "MYR" -> "RM"
        "EUR" -> "€"
        "JPY" -> "¥"
        "AUD" -> "A$"
        "GBP" -> "£"
        else -> code + " "
    }

    /** minor units -> "Rp1.250.000" style string (Indonesian grouping). */
    fun format(minor: Long, currency: String = "IDR", withSign: Boolean = false): String {
        val nf = NumberFormat.getNumberInstance(idLocale).apply { maximumFractionDigits = 0 }
        val abs = nf.format(kotlin.math.abs(minor))
        val core = "${symbol(currency)}$abs"
        return when {
            minor < 0 -> "-$core"
            withSign && minor > 0 -> "+$core"
            else -> core
        }
    }

    /** Compact: Rp1,2 jt */
    fun formatCompact(minor: Long, currency: String = "IDR"): String {
        val v = kotlin.math.abs(minor).toDouble()
        val s = symbol(currency)
        val body = when {
            v >= 1_000_000_000 -> "${trim(v / 1_000_000_000)} M"
            v >= 1_000_000 -> "${trim(v / 1_000_000)} jt"
            v >= 1_000 -> "${trim(v / 1_000)} rb"
            else -> v.toLong().toString()
        }
        return (if (minor < 0) "-" else "") + s + body
    }

    private fun trim(v: Double): String {
        val r = (kotlin.math.round(v * 10) / 10.0)
        return if (r == r.toLong().toDouble()) r.toLong().toString()
        else r.toString().replace('.', ',')
    }

    /** "1250000" or "1.250.000" -> 1250000 minor units. */
    fun parseToMinor(text: String): Long {
        val digits = text.filter { it.isDigit() }
        return digits.toLongOrNull() ?: 0L
    }

    /** minor -> editable digits string. */
    fun minorToDigits(minor: Long): String = minor.toString()
}
