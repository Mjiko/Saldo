package com.jiko.moneytracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Amounts are stored as Long minor units of the base currency (e.g. sen for IDR). */
@Entity(tableName = "transactions")
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "EXPENSE", // EXPENSE | INCOME | TRANSFER
    val amountMinor: Long = 0,
    val accountId: Long = 0,
    val toAccountId: Long? = null, // transfer destination
    val categoryId: Long? = null,
    val note: String = "",
    val tags: String = "", // comma-separated
    val timestamp: Long = System.currentTimeMillis(),
    val recurringId: Long? = null,
    val receiptPath: String? = null // attached receipt photo (app-private filesDir/receipts/)
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val iconKey: String = "more",
    val colorHex: String = "#FF5722",
    val kind: String = "EXPENSE", // EXPENSE | INCOME | BOTH
    val parentId: Long? = null, // subcategory of
    val sortOrder: Int = 0,
    val isCustom: Boolean = false // false = Bawaan (built-in, undeletable); true = Kustom (user-made)
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val currency: String = "IDR",
    val iconKey: String = "wallet",
    val colorHex: String = "#FF5722",
    val sortOrder: Int = 0,
    val archived: Boolean = false
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null, // null = overall monthly budget
    val amountMinor: Long = 0,
    val monthStartDay: Int = 1, // custom payday start, 1..28
    val alertPct: Int = 90 // warn at this % of budget
)

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "EXPENSE",
    val amountMinor: Long = 0,
    val categoryId: Long? = null,
    val accountId: Long = 0,
    val toAccountId: Long? = null,
    val note: String = "",
    val cadence: String = "MONTHLY", // DAILY | WEEKLY | MONTHLY | YEARLY
    val nextDue: Long = System.currentTimeMillis(),
    val autoCreate: Boolean = true,
    val active: Boolean = true
)

/** Savings goal: money earmarked per goal, separate from daily balance. */
@Entity(tableName = "goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val targetMinor: Long = 0,
    val savedMinor: Long = 0,
    val colorHex: String = "#FF5722",
    val accountId: Long = 0,
    val deadline: Long = System.currentTimeMillis() // epoch millis
)

/**
 * Debt / receivable note (Hutang & Piutang). Status is NOT stored: it is
 * derived from the payments — remaining = amount − Σ payments.
 * side: PIUTANG = someone owes the user; HUTANG = the user owes someone.
 */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val side: String = "PIUTANG", // PIUTANG | HUTANG
    val person: String = "",
    val amountMinor: Long = 0,
    val note: String = "",
    val date: Long = System.currentTimeMillis(), // borrowed date (epoch millis)
    val due: Long? = null // optional due date (epoch millis)
)

/** One installment (cicilan) toward a [Debt]. Deleted together with its debt. */
@Entity(tableName = "debt_payments")
data class DebtPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long = 0,
    val amountMinor: Long = 0,
    val date: Long = System.currentTimeMillis() // epoch millis
)
