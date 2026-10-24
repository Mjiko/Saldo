package com.jiko.moneytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Txn::class, Category::class, Account::class, Budget::class, Recurring::class, SavingsGoal::class, Debt::class, DebtPayment::class],
    version = 5,
    exportSchema = false
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun goalDao(): GoalDao
    abstract fun debtDao(): DebtDao

    companion object {
        @Volatile private var instance: MoneyDatabase? = null

        /** v1 -> v2: adds the savings-goals table. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`targetMinor` INTEGER NOT NULL, " +
                        "`savedMinor` INTEGER NOT NULL, " +
                        "`colorHex` TEXT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, " +
                        "`deadline` INTEGER NOT NULL)"
                )
            }
        }

        /** v2 -> v3: receipt photo attachment path on transactions. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receiptPath` TEXT")
            }
        }

        /** v3 -> v4: Hutang & Piutang tables (debts + their installment payments). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`side` TEXT NOT NULL, " +
                        "`person` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`due` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `debt_payments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`debtId` INTEGER NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`date` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v4 -> v5: categories gain the Bawaan/Kustom flag (Kelola Kategori).
         * Seeded built-ins keep isCustom = 0. Rows the pre-v1.6 category pane let
         * users create are recognised heuristically and flagged Kustom (1):
         * subcategories (seeds have none), rows beyond the 14 seed slots
         * (sortOrder >= 14), or rows whose name is not one of the seed names
         * (e.g. a built-in the user renamed in the old pane).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `isCustom` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `categories` SET `isCustom` = 1 WHERE `parentId` IS NOT NULL " +
                        "OR `sortOrder` >= 14 " +
                        "OR `name` NOT IN ('Makanan & Minum','Transportasi','Belanja','Tagihan'," +
                        "'Hiburan','Kesehatan','Pendidikan','Perawatan Diri','Keluarga','Lainnya'," +
                        "'Gaji','Bonus','Bisnis','Investasi')"
                )
            }
        }

        fun get(context: Context): MoneyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneyDatabase::class.java,
                    "saldo.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
