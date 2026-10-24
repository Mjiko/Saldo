package com.jiko.moneytracker.util

import java.util.Calendar
import kotlin.math.abs

/** One detected receipt line item. */
data class ReceiptItem(val name: String, val amount: Long)

/** Best-effort parse of OCR text from an Indonesian receipt. */
data class ParsedReceipt(
    val merchant: String,
    val dateTs: Long?, // null when no date was found on the receipt
    val total: Long?, // null when no payable total was found
    val items: List<ReceiptItem>
)

/**
 * Parses ML Kit OCR lines from Indonesian receipts.
 *
 * Total-reading strategy (v1.3 rework, after real receipts misread):
 *  1. Date/time substrings are stripped from every line BEFORE any amount
 *     extraction, so "20/10/2026" or "12:51:33" digits can never be picked
 *     up as a price; bare year tokens (2024–2026) are rejected as amounts.
 *  2. The payable total is anchored on Indonesian total lines — GRAND TOTAL,
 *     TOTAL BAYAR, TOTAL BELANJA, HARGA TOTAL, JUMLAH (BAYAR), TOTAL —
 *     matched fuzzily (OCR reads "T0TAL", "TOTAI", "GR4ND TOTAL"…) on
 *     squashed, space-free normal forms. The LAST amount on the matched line
 *     wins; if the line carries no amount, the next OCR line is checked.
 *     SUBTOTAL is never the total, and TUNAI/KEMBALI/PPN/DISKON/SERVICE
 *     lines are excluded.
 *  3. Cross-check: when item lines were parsed, a candidate total equal to
 *     (or within ~1% of) the item sum is preferred over the rest.
 *  4. Fallback: the largest amount written in Indonesian thousand-separator
 *     format ("261.006") or Rp-prefixed, at least Rp 1.000.
 *
 * Amounts use "." as thousand separator and "," as decimal ("83.200",
 * "1.250.000,50"), sometimes "Rp"-prefixed; items are name+trailing-amount
 * lines outside the totals/payment block.
 */
object ReceiptParser {

    private val TOKEN = Regex("\\d[\\d.,]*\\d|\\d")

    /** Full date shapes: 20/10/2026, 20-10-26, 2026.10.20 … */
    private val DATE_SHAPE =
        Regex("\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}")

    /** Time shapes: 12:51, 12:51:33. */
    private val TIME_SHAPE = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")

    /** A bare 4-digit year is a year, not a price. */
    private val BARE_YEAR = Regex("^(19|20)\\d{2}$")

    /** Indonesian thousand-grouped token: "261.006", "1.250.000", "12.500,00". */
    private val GROUPED_IDR = Regex("^\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?$")

    private val DATE_DMY = Regex("(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})")
    private val DATE_YMD = Regex("(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})")

    /** Words that disqualify a line from being the payable total. */
    private val TOTAL_EXCLUDE = listOf(
        "TUNAI", "CASH", "KEMBALI", "KEMBALIAN", "CHANGE",
        "PPN", "PAJAK", "DISKON", "POTONGAN", "SUBTOTAL", "SERVICE", "LAYANAN"
    )

    /** Total-keyword tiers, best first; matched fuzzily on squashed forms. */
    private val TOTAL_TIERS: List<List<String>> = listOf(
        listOf("GRANDTOTAL"),
        listOf("TOTALBAYAR", "TOTALBELANJA", "HARGATOTAL", "JUMLAHBAYAR"),
        listOf("JUMLAH"),
        listOf("TOTAL")
    )

    private val ITEM_BLOCK = listOf(
        "TOTAL", "SUBTOTAL", "TUNAI", "KEMBALI", "KEMBALIAN", "PPN", "PAJAK", "TAX",
        "DISKON", "DISCOUNT", "TERIMA KASIH", "KASIR", "TELP", "TLP", "NPWP", "BAYAR",
        "JUMLAH", "LAYANAN", "CALL CENTER", "CUSTOMER", "SERVICE"
    )

    /** Remove date/time substrings so their digits never become amounts. */
    fun stripDateTime(line: String): String =
        line.replace(DATE_SHAPE, " ").replace(TIME_SHAPE, " ")

    /** "83.200" / "1.250.000,50" / "83,200.00" / "83200" -> rupiah Long, or null. */
    fun parseAmountToken(raw: String): Long? {
        val t = raw.replace("Rp", "", ignoreCase = true).trim()
        return when {
            // Indonesian: dots group thousands, comma decimals
            t.matches(Regex("^\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?$")) ->
                t.substringBefore(',').replace(".", "").toLongOrNull()
            // US style: commas group thousands, dot decimals
            t.matches(Regex("^\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?$")) ->
                t.substringBefore('.').replace(",", "").toLongOrNull()
            t.matches(Regex("^\\d+,\\d{1,2}$")) -> t.substringBefore(',').toLongOrNull()
            t.matches(Regex("^\\d+$")) -> t.toLongOrNull()
            else -> null
        }
    }

    /** All plausible rupiah amounts on a line (>= 100). Dates/times and bare
     *  years are stripped/rejected first; phone-like tokens are skipped. */
    fun amountsIn(line: String): List<Long> =
        TOKEN.findAll(stripDateTime(line)).mapNotNull { m ->
            val raw = m.value
            when {
                raw.length >= 10 && raw.startsWith("0") -> null // phone / member numbers
                BARE_YEAR.matches(raw) -> null // a year, not a price
                else -> parseAmountToken(raw)
            }
        }.filter { it >= 100 }.toList()

    fun parse(lines: List<String>, now: Long = System.currentTimeMillis()): ParsedReceipt {
        val clean = lines.map { it.trim() }.filter { it.isNotBlank() }
        val items = findItems(clean)
        return ParsedReceipt(
            merchant = findMerchant(clean),
            dateTs = findDate(clean, now),
            total = findTotal(clean, items),
            items = items
        )
    }

    // ------------------------------------------------------------------
    // Fuzzy keyword matching
    // ------------------------------------------------------------------

    /**
     * Squashed (letters-only, space-free) normal forms of a line for keyword
     * matching. OCR confuses 0/O, 1/I, I/L, Λ/A and 4/A and splits words with
     * stray spaces, so several variants are produced and a keyword matches if
     * ANY variant contains it: "T0TAL"→TOTAL, "TOTAI"→(I→L)→TOTAL,
     * "GR4ND TOTAL"→GRANDTOTAL, "TO TAL"→TOTAL.
     */
    private fun squashedForms(line: String): List<String> {
        val mapped = line.uppercase()
            .replace('Λ', 'A') // Greek lambda, a common OCR artifact for A
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('4', 'A')
        val squashed = mapped.replace(Regex("[^A-Z]"), "")
        // Second variant with I read as L ("TOTAI" for TOTAL).
        return listOf(squashed, squashed.replace('I', 'L'))
    }

    private fun matchesAny(line: String, keywords: List<String>): Boolean {
        val forms = squashedForms(line)
        return keywords.any { kw -> forms.any { it.contains(kw) } }
    }

    // ------------------------------------------------------------------
    // Total
    // ------------------------------------------------------------------

    private data class TotalCandidate(val amount: Long, val tier: Int)

    /** One candidate per keyword tier (the LAST matching line of a tier
     *  wins — the real total sits at the bottom of the receipt). */
    private fun totalCandidates(lines: List<String>): List<TotalCandidate> {
        val found = mutableListOf<TotalCandidate>()
        lines.forEachIndexed { i, line ->
            if (matchesAny(line, TOTAL_EXCLUDE)) return@forEachIndexed
            TOTAL_TIERS.forEachIndexed { tier, kws ->
                if (!matchesAny(line, kws)) return@forEachIndexed
                // Rightmost amount on the total line itself…
                var amt = amountsIn(line).lastOrNull()
                if (amt == null && i + 1 < lines.size) {
                    // …or, when the label stands alone, on the next OCR line —
                    // unless that line is an excluded/other-keyword line.
                    val next = lines[i + 1]
                    if (!matchesAny(next, TOTAL_EXCLUDE) &&
                        !matchesAny(next, TOTAL_TIERS.flatten())
                    ) {
                        amt = amountsIn(next).lastOrNull()
                    }
                }
                if (amt != null) found.add(TotalCandidate(amt, tier))
            }
        }
        return TOTAL_TIERS.indices.mapNotNull { t -> found.lastOrNull { it.tier == t } }
    }

    /** Largest plausible amount (>= Rp 1.000) that LOOKS like a printed price:
     *  Indonesian thousand-grouped ("261.006") or on an Rp-prefixed line. */
    private fun fallbackTotal(lines: List<String>): Long? {
        var best: Long? = null
        for (line in lines) {
            if (matchesAny(line, TOTAL_EXCLUDE)) continue
            val clean = stripDateTime(line)
            val rpLine = clean.contains("Rp", ignoreCase = true)
            for (m in TOKEN.findAll(clean)) {
                val raw = m.value
                if (BARE_YEAR.matches(raw)) continue
                if (!rpLine && !GROUPED_IDR.matches(raw)) continue
                val v = parseAmountToken(raw) ?: continue
                if (v >= 1000 && (best == null || v > best!!)) best = v
            }
        }
        return best
    }

    private fun findTotal(lines: List<String>, items: List<ReceiptItem>): Long? {
        val keyword = totalCandidates(lines)
        val fallback = fallbackTotal(lines)
        val ordered = keyword.map { it.amount } + listOfNotNull(fallback)
        // Cross-check against the parsed items: a candidate that equals the
        // item sum (within ~1%, rounding on receipts) beats any other guess.
        val itemSum = items.sumOf { it.amount }
        if (itemSum > 0) {
            val tol = maxOf(1L, itemSum / 100)
            ordered.firstOrNull { abs(it - itemSum) <= tol }?.let { return it }
        }
        return keyword.firstOrNull()?.amount ?: fallback
    }

    // ------------------------------------------------------------------
    // Merchant / date / items
    // ------------------------------------------------------------------

    private fun findMerchant(lines: List<String>): String {
        for (line in lines.take(6)) {
            val letters = line.count { it.isLetter() }
            if (letters < 3) continue
            val up = line.uppercase()
            if (up.startsWith("JL") || up.startsWith("JLN") || up.contains("TELP") ||
                up.contains("NPWP") || up.contains("INDONESIA") && letters < 12
            ) continue
            return if (line == up) titleCase(line) else line
        }
        return ""
    }

    private fun titleCase(s: String): String =
        s.lowercase().split(Regex("\\s+")).joinToString(" ") { w ->
            w.replaceFirstChar { c -> c.uppercase() }
        }

    private fun findDate(lines: List<String>, now: Long): Long? {
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        fun build(y: Int, mo: Int, d: Int): Long? {
            if (mo !in 1..12 || d !in 1..31) return null
            val year = if (y < 100) 2000 + y else y
            if (year !in 2000..2100) return null
            val c = Calendar.getInstance()
            c.set(
                year, mo - 1, d,
                nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE), 0
            )
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        for (line in lines) {
            DATE_YMD.find(line)?.let { m ->
                build(
                    m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()
                )?.let { return it }
            }
            DATE_DMY.find(line)?.let { m ->
                build(
                    m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()
                )?.let { return it }
            }
        }
        return null
    }

    private fun findItems(lines: List<String>): List<ReceiptItem> {
        val out = mutableListOf<ReceiptItem>()
        for (line in lines) {
            val up = line.uppercase()
            if (ITEM_BLOCK.any { up.contains(it) }) continue
            if (DATE_DMY.containsMatchIn(line) || DATE_YMD.containsMatchIn(line)) continue
            // Amounts come from the date/time-stripped line so fragments of a
            // printed date can never pose as an item price.
            val clean = stripDateTime(line)
            val matches = TOKEN.findAll(clean).toList()
            if (matches.isEmpty()) continue
            val last = matches.last()
            if (BARE_YEAR.matches(last.value)) continue
            val amount = parseAmountToken(last.value)
            if (amount == null || amount < 100) continue
            var name = clean.substring(0, last.range.first).trim()
            // Drop trailing qty/unit-price fragments: " x2", " @ 3.200", ":" etc.
            name = name.replace(Regex("\\s*[@xX]\\s*$"), "")
                .replace(Regex("[\\s:;\\-]+$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (name.count { it.isLetter() } < 2) continue
            if (name.length > 48) name = name.take(48).trim()
            out.add(ReceiptItem(name, amount))
            if (out.size >= 30) break
        }
        return out
    }
}
