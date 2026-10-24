package com.jiko.moneytracker.data

/** Default categories (Indonesian) + starter accounts, seeded on first run. */
object Seed {
    // name, iconKey, colorHex, kind
    val categories = listOf(
        // Expense
        Quad("Makanan & Minum", "food", "#FF5722", "EXPENSE"),
        Quad("Transportasi", "car", "#7C4DFF", "EXPENSE"),
        Quad("Belanja", "bag", "#FF9100", "EXPENSE"),
        Quad("Tagihan", "bill", "#00B0FF", "EXPENSE"),
        Quad("Hiburan", "fun", "#D6339E", "EXPENSE"),
        Quad("Kesehatan", "health", "#00C853", "EXPENSE"),
        Quad("Pendidikan", "edu", "#3D5AFE", "EXPENSE"),
        Quad("Perawatan Diri", "spa", "#FF4081", "EXPENSE"),
        Quad("Keluarga", "family", "#D9A404", "EXPENSE"),
        Quad("Lainnya", "more", "#9E9E9E", "BOTH"),
        // Income
        Quad("Gaji", "work", "#00C853", "INCOME"),
        Quad("Bonus", "gift", "#00BFA5", "INCOME"),
        Quad("Bisnis", "shop", "#64DD17", "INCOME"),
        Quad("Investasi", "chart", "#18FFFF", "INCOME"),
    )

    val accounts = listOf(
        Triple("Tunai", "wallet", "#FF5722"),
        Triple("Bank", "bank", "#3D5AFE"),
        Triple("Dompet Digital", "phone", "#00B0FF"),
    )

    data class Quad(val name: String, val icon: String, val color: String, val kind: String)

    suspend fun run(db: MoneyDatabase) {
        if (db.categoryDao().count() == 0) {
            categories.forEachIndexed { i, c ->
                db.categoryDao().insert(Category(name = c.name, iconKey = c.icon, colorHex = c.color, kind = c.kind, sortOrder = i))
            }
        }
        if (db.accountDao().count() == 0) {
            accounts.forEachIndexed { i, a ->
                db.accountDao().insert(Account(name = a.first, iconKey = a.second, colorHex = a.third, sortOrder = i))
            }
        }
    }
}
