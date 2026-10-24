package com.jiko.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import com.jiko.moneytracker.util.MoneyFormat

/**
 * Amount-first keypad, web 4-column grid:
 * 1 2 3 ÷ / 4 5 6 × / 7 8 9 − / 000 0 DEL + / Bersih, =, Simpan (span 2).
 * Save key lives in the keypad; calculator behaviour unchanged.
 */
@Composable
fun Keypad(
    initialMinor: Long,
    currency: String,
    onSave: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expr by remember(initialMinor) {
        mutableStateOf(if (initialMinor > 0) MoneyFormat.minorToDigits(initialMinor) else "")
    }
    val preview = remember(expr) { tryEval(expr) }
    val pal = saldoColors
    val shown = preview ?: MoneyFormat.parseToMinor(expr)

    fun press(k: String) {
        expr = when (k) {
            "⌫" -> expr.dropLast(1)
            "C" -> ""
            "=" -> (tryEval(expr)?.toString() ?: expr)
            in setOf("+", "−", "×", "÷") -> {
                val op = mapOp(k)
                when {
                    expr.isEmpty() -> expr
                    expr.last() in "+-×÷" -> expr.dropLast(1) + op
                    else -> expr + op
                }
            }
            else -> (expr + k).take(15)
        }
    }

    Column(modifier) {
        // Amount display: "Rp" 15/700 muted + value 40/800, expression line below
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                MoneyFormat.symbol(currency),
                fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = pal.muted, modifier = Modifier.padding(bottom = 7.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                MoneyFormat.format(shown, currency).removePrefix(MoneyFormat.symbol(currency)),
                fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp,
                letterSpacing = (-1.2).sp, color = pal.ink,
                style = tnumStyle, maxLines = 1
            )
        }
        if (expr.isNotEmpty() && expr != shown.toString()) {
            Text(
                expr.replace("-", "−"),
                fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = pal.faint, style = tnumStyle,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))

        val rows = listOf(
            listOf("1", "2", "3", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("7", "8", "9", "−"),
            listOf("000", "0", "⌫", "+"),
        )
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { k -> GridKey(k, Modifier.weight(1f)) { press(k) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            GridKey("Bersih", Modifier.weight(1f)) { press("C") }
            GridKey("=", Modifier.weight(1f)) { press("=") }
            // Simpan (span 2)
            Box(
                modifier = Modifier.weight(2f).height(52.dp)
                    .shadow(2.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal.successFill)
                    .clickable { onSave(tryEval(expr) ?: 0L) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = "Simpan",
                        tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GridKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val pal = saldoColors
    val isOp = label in setOf("÷", "×", "−", "+")
    Box(
        modifier = modifier.height(52.dp)
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (label) {
            "⌫" -> Icon(Icons.Filled.Backspace, contentDescription = "Hapus",
                tint = pal.accent, modifier = Modifier.size(21.dp))
            "Bersih" -> Text("Bersih", fontFamily = Jakarta, fontSize = 14.sp, color = pal.muted)
            "=" -> Text("=", fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = 21.sp, color = pal.ink)
            else -> Text(
                label, fontFamily = Jakarta, fontWeight = FontWeight.Bold,
                fontSize = if (isOp) 19.sp else 21.sp,
                color = if (isOp) pal.accent else pal.ink,
                style = tnumStyle
            )
        }
    }
}

private fun mapOp(k: String) = when (k) { "−" -> "-"; "×" -> "×"; "÷" -> "÷"; else -> "+" }

/** Evaluate simple expressions with × ÷ precedence. Returns null if invalid. */
fun tryEval(expr: String): Long? {
    if (expr.isBlank()) return null
    return try {
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        expr.forEach { c ->
            if (c.isDigit()) cur.append(c)
            else {
                if (cur.isNotEmpty()) { tokens.add(cur.toString()); cur.clear() }
                tokens.add(c.toString())
            }
        }
        if (cur.isNotEmpty()) tokens.add(cur.toString())
        if (tokens.isEmpty()) return null
        // × ÷ first
        val st = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if ((t == "×" || t == "÷") && st.isNotEmpty() && i + 1 < tokens.size) {
                val a = st.removeLast().toDouble()
                val b = tokens[i + 1].toDouble()
                val r = if (t == "×") a * b else if (b != 0.0) a / b else return null
                st.add(r.toLong().toString()); i += 2
            } else { st.add(t); i++ }
        }
        var acc = st[0].toDouble()
        var j = 1
        while (j < st.size) {
            val op = st[j]; val b = st[j + 1].toDouble()
            acc = if (op == "+") acc + b else acc - b
            j += 2
        }
        acc.toLong().takeIf { it >= 0 }
    } catch (_: Exception) { null }
}
