# Saldo — Money Tracker

**Dibuat oleh Jiko** · *Made by Jiko*

Saldo is a fully offline personal money tracker for Android, built natively in Kotlin with Jetpack Compose. It tracks income and expenses in Indonesian Rupiah (IDR) with a Bahasa Indonesia interface — no account, no server, no ads. Your financial data never leaves your phone unless you export or back it up yourself.

Current release: **v1.7** (versionCode 8)

## Features

- **Transactions** — fast add flow with a custom keypad, accounts, notes, and search/filter history
- **Budgets** — per-category monthly budgets with progress tracking
- **Hutang & Piutang** — debt and receivables tracker with installment (cicilan) payments, overdue indicators, and settled history
- **Custom categories** — create your own categories with 27 icons and 11 colors; deleting a category safely reassigns its data
- **Recurring transactions** — scheduled income/expenses that post automatically
- **Savings goals & forecast** — savings targets and a balance forecast (*Prakiraan Saldo*) projected from your recurring patterns
- **Reports & stats** — monthly reports (*Laporan Bulanan*), charts, calendar view, and a financial-health score
- **Receipt scanning** — on-device OCR (ML Kit) parses Indonesian receipts into draft transactions
- **Backup** — manual CSV export and JSON backup/restore, plus automatic backup to a Google Drive folder you choose (on-change / daily / weekly via WorkManager)
- **Security** — optional PIN and biometric lock; the app works 100% offline

## Tech stack

- **Language/UI:** Kotlin, Jetpack Compose (Material 3)
- **Architecture:** single-module MVVM, Room database (SQLite), DataStore-style preferences
- **Background work:** WorkManager (recurring posting, automatic backups)
- **ML:** Google ML Kit on-device text recognition for receipt OCR
- **Design:** minimalist dark/light themes with a coral accent, Plus Jakarta Sans typography, custom adaptive icon

## Project layout

```
app/src/main/java/com/jiko/moneytracker/
├── MainActivity.kt        # entry point, lock gate
├── MoneyViewModel.kt      # app state
├── data/                  # Room entities, DAOs, database, seed data
├── ui/screens/            # Home, Transactions, Budgets, Debts, Goals,
│                          # Recurring, Reports, Stats, Calendar, Settings…
├── ui/components/         # keypad, charts, icons, receipt image handling
├── ui/theme/              # Compose theme
└── util/                  # backup, auto-backup, CSV, forecast, receipt OCR/parsing
```

## Build & signing

Requirements: Android Studio (or a JDK 17 + Android SDK 34 setup).

```bash
./gradlew assembleDebug     # debug build
./gradlew assembleRelease   # release build (needs signing properties, below)
```

Signing credentials are **not** committed to this repository. To produce a signed build, create your own keystore and pass its details as Gradle properties (for example in your local `~/.gradle/gradle.properties`, never in the repo):

```properties
signing.storeFile=path/to/your.keystore
signing.storePassword=…
signing.keyAlias=…
signing.keyPassword=…
```

## Screenshots

<!-- Add screenshot<img width="478" height="974" alt="Screenshot 2026-10-24 115740" src="https://github.com/user-attachments/assets/f7323b89-6bf0-4820-a4f2-72f104b70624" />
<img width="485" height="972" alt="Screenshot 2026-10-24 115732" src="https://github.com/user-attachments/assets/8b76e0cc-6578-46ad-a4c9-41795b05c4fd" />
<img width="479" height="950" alt="Screenshot 2026-10-24 115723" src="https://github.com/user-attachments/assets/41caceb7-d938-400b-a1d4-039494f400cc" />
<img width="482" height="969" alt="Screenshot 2026-10-24 115713" src="https://github.com/user-attachments/assets/ef0670dc-7fa7-4805-8ad1-0a2cdafd27a9" />
s here: docs/screenshots/ -->

## License & credit

Portfolio project by **Jiko**. The in-app About screen carries the creator credit: *"Dibuat oleh Jiko"*.

---

Saldo — pencatat keuangan pribadi offline untuk Android. **Dibuat oleh Jiko.**
