package com.jiko.moneytracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun all(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun allList(): List<Txn>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun inRange(from: Long, to: Long): Flow<List<Txn>>

    @Query("""SELECT * FROM transactions
              WHERE (:q = '' OR note LIKE '%' || :q || '%' OR CAST(amountMinor AS TEXT) LIKE '%' || :q || '%')
              AND (:type = 'ALL' OR type = :type)
              AND (:accountId = -1 OR accountId = :accountId OR toAccountId = :accountId)
              AND (:categoryId = -1 OR categoryId = :categoryId)
              AND timestamp BETWEEN :from AND :to
              ORDER BY timestamp DESC""")
    fun search(q: String, type: String, accountId: Long, categoryId: Long, from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): Txn?

    @Query("UPDATE transactions SET categoryId = :toId WHERE categoryId IN (:fromIds)")
    suspend fun reassignCategory(fromIds: List<Long>, toId: Long)

    @Insert suspend fun insert(t: Txn): Long
    @Update suspend fun update(t: Txn)
    @Delete suspend fun delete(t: Txn)
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END),0) FROM transactions WHERE timestamp BETWEEN :from AND :to")
    fun expenseSum(from: Long, to: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END),0) FROM transactions WHERE timestamp BETWEEN :from AND :to")
    fun incomeSum(from: Long, to: Long): Flow<Long>

    // Balance per account: income adds, expense subtracts; transfers move between accounts.
    @Query("""SELECT COALESCE(SUM(CASE
        WHEN type='INCOME' AND accountId=:accountId THEN amountMinor
        WHEN type='EXPENSE' AND accountId=:accountId THEN -amountMinor
        WHEN type='TRANSFER' AND accountId=:accountId THEN -amountMinor
        WHEN type='TRANSFER' AND toAccountId=:accountId THEN amountMinor
        ELSE 0 END),0) FROM transactions""")
    fun balanceOf(accountId: Long): Flow<Long>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun all(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun allList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): Category?

    @Insert suspend fun insert(c: Category): Long
    @Update suspend fun update(c: Category)
    @Delete suspend fun delete(c: Category)
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder, name")
    suspend fun childrenOf(parentId: Long): List<Category>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    fun all(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, name")
    suspend fun allList(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): Account?

    @Insert suspend fun insert(a: Account): Long
    @Update suspend fun update(a: Account)
    @Delete suspend fun delete(a: Account)
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun all(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets")
    suspend fun allList(): List<Budget>

    @Insert suspend fun insert(b: Budget): Long
    @Update suspend fun update(b: Budget)
    @Delete suspend fun delete(b: Budget)
    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun byId(id: Long): Budget?

    @Query("UPDATE budgets SET categoryId = :toId WHERE categoryId IN (:fromIds)")
    suspend fun reassignCategory(fromIds: List<Long>, toId: Long)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY nextDue")
    fun all(): Flow<List<Recurring>>

    @Query("SELECT * FROM recurring ORDER BY nextDue")
    suspend fun allList(): List<Recurring>

    @Query("SELECT * FROM recurring WHERE active = 1 AND nextDue <= :now ORDER BY nextDue")
    suspend fun due(now: Long): List<Recurring>

    @Insert suspend fun insert(r: Recurring): Long
    @Update suspend fun update(r: Recurring)
    @Delete suspend fun delete(r: Recurring)
    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun byId(id: Long): Recurring?

    @Query("UPDATE recurring SET categoryId = :toId WHERE categoryId IN (:fromIds)")
    suspend fun reassignCategory(fromIds: List<Long>, toId: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id")
    fun all(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM goals ORDER BY id")
    suspend fun allList(): List<SavingsGoal>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun byId(id: Long): SavingsGoal?

    @Insert suspend fun insert(g: SavingsGoal): Long
    @Update suspend fun update(g: SavingsGoal)
    @Delete suspend fun delete(g: SavingsGoal)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY date DESC")
    fun all(): Flow<List<Debt>>

    @Query("SELECT * FROM debts ORDER BY date DESC")
    suspend fun allList(): List<Debt>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun byId(id: Long): Debt?

    @Insert suspend fun insert(d: Debt): Long
    @Update suspend fun update(d: Debt)
    @Delete suspend fun delete(d: Debt)

    @Query("SELECT * FROM debt_payments ORDER BY date, id")
    fun allPayments(): Flow<List<DebtPayment>>

    @Query("SELECT * FROM debt_payments ORDER BY date, id")
    suspend fun allPaymentsList(): List<DebtPayment>

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY date, id")
    suspend fun paymentsOf(debtId: Long): List<DebtPayment>

    @Insert suspend fun insertPayment(p: DebtPayment): Long

    @Query("DELETE FROM debt_payments WHERE debtId = :debtId")
    suspend fun deletePaymentsOf(debtId: Long)
}
