# Saldo v1.1 — Web-Exact Design Spec (extracted from ~/workspace/ts-spaces/saldo-web/index.html)

Goal: native Compose UI must mirror the web version token-for-token and screen-for-screen.
Keep ALL existing logic/functionality. Change visuals, layout order, nav structure; add Goals + Health Score.

## 1. Design tokens (EXACT from web CSS)

### Light
- bg #F6F7F9, surface #FFFFFF, surface2 #ECEFF3, surface3 #E2E7EE
- ink #10151D, muted #5A6472, faint #98A1AF, line #E4E8EE
- accent #FF5722, accentInk #FFFFFF, accentSoft #FFE9E2
- successText #00A047, successFill #00C853
- warningText #E07F00, warningFill #FF9100
- danger #D32F2F, dangerSoft #FDECEC
- alertIconBg #FFF3E0, alertIconFg #E07F00
- navBg white @ 92% alpha

### Dark (obsidian)
- bg #0B0F19, surface #141A26, surface2 #1C2333, surface3 #242E44
- ink #F2F4F8, muted #9AA4B2, faint #67707F, line #232B3D
- accent #FF8A50 (lighter coral!), accentInk #1A0E08, accentSoft #332016 @ ~48% alpha
- successText = successFill #00E676
- warningText #FFAB40, warningFill #FF9100
- danger #FF6B6B, dangerSoft #3A1E22
- alertIconBg #33240F, alertIconFg #FFAB40 (warningText)
- navBg #0B0F19 @ 92% alpha

### Shape / elevation
- Card: radius 18dp, padding 16dp, soft shadow (use CardDefaults elevation 1–2dp), NO border.
- Web shadows: light `0 1px 2px rgba(16,21,29,.05), 0 8px 24px -12px rgba(16,21,29,.12)`; dark stronger black. Compose approximation is fine.
- iconbtn: 38dp, radius 12dp, surface bg + card shadow, icon muted.
- Sheet: top radius 26dp, container color = **bg** (not surface), grab handle 38x4.5dp pill in surface3, centered, 12dp below top.
- FAB: 52dp, radius **18dp** (rounded square), accent bg, raised -22dp above bar, colored shadow (approximation OK).
- Switch: 46x27dp pill, track surface3 off / successFill on, white knob 21dp. (Material3 Switch styled close enough is acceptable if custom is costly — prefer a small custom switch to match; it appears in Settings + Lock.)

### Typography — Plus Jakarta Sans (400/500/600/700/800)
Try to embed the font: fetch TTFs (css2 API with a browser UA gives ttf URLs) into `app/src/main/res/font/` as `plus_jakarta_sans_{regular,medium,semibold,bold,extrabold}` and define a FontFamily used by ALL text styles. If the download fails (egress), fall back to FontFamily.Default and note it in your report.
Scale (sp / weight):
- topbar title 21 / 800, letterSpacing -0.02em
- hero balance 36 / 800, ls -0.03em; its "Rp" prefix 19 / 700 muted
- hero label 12.5 / 500 muted; greeting date sub 13 / 500 muted
- section title 15 / 700, ls -0.01em; section link 12.5 / 600 accent; section span 12 muted
- card big value (accounts total, budgets total) 26 / 800 (accounts) or 17 / 800 (budget total row)
- tx name 14 / 600; tx sub 12 muted; tx amount 14 / 700; tx account 11 faint
- day label 12 / 700 muted, UPPERCASE, ls 0.04em; day net 12 / 600
- legend name 13 / 600; legend pct 12 muted (36dp wide, right); legend amount 12.5 / 700 (76dp, right)
- donut center label 11 / 500 muted; center value 16.5 / 800
- budget name 14 / 600; budget pct 12 / 700; budget meta 12 muted
- inout label 11.5 / 500 muted; inout value 15.5 / 700
- chip name 13 / 600; chip amount 12.5 / 600 muted
- menu name 14 / 600; menu desc 12 / 500 muted
- tab label 10.5 / 600
- sheet title 17 / 800; amount value 40 / 800 ls -0.03em; amount "Rp" 15 / 700 muted; expr line 13 / 600 faint
- field label 12 / 700 muted; input text 14
- keypad digit 21 / 700; op 19 accent; save 15 white
- health score (inside ring) 17 / 800; verdict 14.5 / 700(bold); health rows 12.5 muted
- goal name 14.5 / 700; goal meta 12 muted; goal ring % 12 / 800
- All money text: tabular nums (fontFeatureSettings "tnum").

### Spacing
- Screen horizontal padding 18dp. Topbar padding: 18dp sides, top 18dp, bottom 6dp.
- Section title margins: top 22dp, bottom 10dp, horizontal 2dp inset.
- Cards stack with 12–14dp gaps.

## 2. Shared components (restyle in components/)

- **SaldoCard**: surface bg, radius 18, padding 16, soft elevation. Use everywhere web uses `.card`.
- **TopBar(title, sub?, onBack?, trailing?)**: row with optional back iconbtn (arrow), title 21/800 (flex), optional sub text right (12.5 muted) or trailing iconbtn(s). Web sub-screens (calendar, recurring, goals, accounts, settings) have back iconbtn; budgets has NO back, title + plus iconbtn.
- **SectionTitle(text, span?, linkText?, onLink?)** per tokens above.
- **CategoryIcon**: change from circle+tint to **solid color rounded square**, white icon: tx rows 40dp r13; budget rows 36dp r11; menu rows 36dp r11; catgrid 42dp r14. (Account chip dots stay circles 26dp / picker dots 20dp.)
- **TxnRow (web layout)**: inside day-group cards. Icon box 40dp r13 solid cat color (TRANSFER: swap icon, color #5A6472; no cat: #9E9E9E). Title = category name (or "Transfer"); sub = [subcategory?, note] joined " · ", fallback = formatted date. Right column: amount (INCOME "+" successText color; EXPENSE "−" ink color; TRANSFER plain ink) 14/700, below it account name (or "From → To" for transfers) 11 faint. Rows separated by 1dp line hairlines, row vertical padding 11dp, card horizontal padding 14dp.
  - Subcategory: native Txn has no subcat field — the native model stores subcategory as a child Category (parentId). Resolve: if txn.categoryId points to a child category, title = parent name, sub starts with child name. Keep native click/long-press behaviors.
- **ProgressRing(pct, color, sizeDp, strokeDp, centerContent)**: Canvas; track = surface2 full circle; arc from -90°, round cap; matches web ringSVG (used for goals 52dp/9dp stroke, health 74dp/10dp).
- **Donut + legend**: keep existing DonutChart math but restyle: size 148dp, stroke width ~15dp, round caps, small gaps between segments, center = label 11 muted + value 16.5/800. Layout = **column**: donut centered on top, legend BELOW full width (web stacks, not side-by-side). Legend row: 9dp swatch r3, name, pct (36dp right), amount (76dp right); row padding 6dp; tap row or segment → drill.
- **BudgetRow**: as in tokens; bar height 7dp radius full, track surface2; fill color rule: pct>=1 → danger; pct>=0.9 → warningText; pct>=0.75 → warningFill; else category color. Meta: "{spent} dari {limit} · {Sisa X | Lewat X}" with status part colored + 600 weight. Pct text colored same.
- **MenuRow**: icon box 36dp r11 **solid** color bg (per-row brand color, white icon), name 14/600 + optional BARU badge (accentSoft pill, accent text 10/800, radius full, padding 2x6), desc 12 muted below name, chevron-right faint at end. Rows in a SaldoCard-list (padding horiz 14, row vert 13, hairline dividers).
- **SegControl**: container surface2, radius 12, padding 3; buttons radius 9, 12.5/600 muted; selected = surface bg + ink text + small shadow.
- **TypeSeg (add sheet)**: container surface2 radius 13 padding 3; buttons 13/700 muted; selected bg surface + colored text: EXPENSE→accent, INCOME→successText, TRANSFER→ink; small shadow.
- **FilterChip**: pill, surface bg + shadow, 12.5/600 muted; ON = ink bg + bg-colored text.
- **SearchBar**: 44dp height, radius 14, surface bg + shadow, search icon faint, placeholder faint "Cari catatan, kategori, tag…".
- **Keypad** (restyle Keypad.kt to web grid): 4 columns, gap 5dp. Rows: 1 2 3 ÷ / 4 5 6 × / 7 8 9 − / 000 0 DEL + / Bersih, =, Simpan(span 2). Buttons 52dp height, radius 14, surface bg + shadow, digits 21/700 ink, ops accent 19, DEL = backspace icon accent, "Bersih" 14 muted, Simpan = successFill bg, white text 15/700 with check icon. Keep existing calculator-expression behavior/logic of the native keypad — only restyle + rearrange to this grid (native ops are + - × ÷; map glyphs: − is minus).
- **EmptyState**: card with 52dp r16 surface2 icon box (wallet icon, faint), bold title 14.5, muted sub 13, centered.
- **Toast**: keep Android Toast via vm (fine).

## 3. Navigation (MainActivity restructure — match web tabbar)

Tabs (in order): Beranda(home, Home icon), Transaksi(txns, List icon), **center FAB**, Statistik(stats, PieChart icon), Lainnya(more, GridView icon).
- Budgets is NO longer a tab: route "budgets" reached from Lainnya; active-tab mapping (web TABMAP): budgets/calendar/recurring/goals/accounts/settings → "more" tab highlighted; txns → "tx".
- Bottom bar visible on ALL routes (like web). Custom bar (not BottomAppBar, to allow FAB overflow): Row with navBg color (surface @92% + blur not available — solid surface color acceptable), top hairline divider (line color), bottom padding = navigationBars inset + 8dp, top padding 6dp. Tab item: Column centered, icon 21dp, label 10.5/600; active color accent, inactive faint; NO pill indicator. FAB: 52dp, RoundedCornerShape(18dp), containerColor accent, contentColor accentInk, offset y = -22dp, elevation 8dp.
- MoreScreen menu (web order/colors):
  1. Anggaran — target icon — #FF5722 — "Batas bulanan per kategori"
  2. Kalender — calendar icon — #00B0FF — "Pengeluaran per tanggal"
  3. Transaksi Berulang — repeat icon — #7C4DFF — "{n} jadwal aktif"
  4. Target Tabungan — sparkles icon — #00C853 — "Baru — nabung per tujuan" + BARU badge
  5. Akun & Dompet — wallet icon — #3D5AFE — "{n} akun · {total compact}"
  6. Pengaturan — gear icon — #5A6472 — "Tema, kunci PIN, ekspor & cadangan"
  Below: info card: bold "Saldo" 14 + muted 12.5 text "Pelacak pengeluaran & anggaran yang minimalis, sepenuhnya luring, tanpa akun. Versi 1.1".
  Header: TopBar "Lainnya" (no back).

## 4. Screens

### Beranda (Home) — order exactly:
1. TopBar: left column: sub = today formatted "EEEE, d MMMM yyyy" (Locale id; e.g. "Selasa, 20 Oktober 2026"), title "Halo, Jiko". Right: eye iconbtn toggling hideBal (eye / eye-off icon). (Persist hideBal in Prefs — add field `hideBalance` default false.)
2. Hero (NOT a card): label "Total saldo · {n} akun" (12.5 muted, eye-adjacent is in topbar); balance: annotated string "Rp" 19/700 muted + " " + grouped number 36/800; when hidden show "Rp •••••••".
3. InOut row: two cards (radius 16, padding 12/14) gap 10: circle 34dp icon (income: arrow-down, bg successFill @14% alpha, tint successText; expense: arrow-up, bg accentSoft, tint accent), label "Pemasukan · {Mon}" / "Pengeluaran · {Mon}" (current month short "Okt"), value 15.5/700 (compact-hidden → "••••••"). Tap → Statistik.
4. Savings strip (12.5 muted, padding horiz 2): if monthNet >= 0: sparkles icon 14 + "Bulan ini kamu menabung **{compact net}** — pertahankan ritmenya." (net in successText bold); else warning bell icon + "Pengeluaran bulan ini melebihi pemasukan sebesar **{compact}**" with amount warningText bold.
   (Web appends "— gaji masuk tanggal 25" in the deficit case from example data; skip that clause natively.)
5. SectionTitle "Akun" → chips LazyRow: chip = surface pill (shadow), 26dp circle dot solid account color + white account icon 13dp, name 13/600, amount 12.5/600 muted compact. Tap chip → Akun & Dompet screen. ("Semua" ink chip is NOT in home web render — skip.)
6. Alert banner (only if any budget pct >= 0.75 in current budget period; pick highest pct): card radius 16 padding 13/14, row: 34dp r11 alertIconBg box with bell icon alertIconFg; bold 13.5 "Anggaran {cat} sudah {pct}%"; sub 12.5 muted: pct>=1 → "Sudah melewati batas {limit} bulan ini." else "Mendekati batas {limit} — hati-hati di sisa bulan ini." Tap → Anggaran.
7. SectionTitle "Pengeluaran bulan ini" + link "Statistik" → donut card (donut + legend, top 5 categories by expense this calendar month; center label "Total keluar", value compact total). Tap → drill (existing behavior).
8. SectionTitle "Target tabungan" + span "baru" + link "Lihat semua" → Goals card: single row = top goal by progress %: ProgressRing 52/9 with % center, name 14.5/700, meta "{saved} dari {target}", "+ Isi" button (accentSoft bg, accent text 12.5/700, radius 10, padding 8x12) → fill-goal dialog. If no goals: row text "Belum ada target — buat target pertamamu" + button "Buat" → goals screen. (Section only makes sense with goals feature — always show.)
9. SectionTitle "Transaksi terakhir" + link "Lihat semua" → day-grouped? NO — web home recent = flat list of 5 latest inside ONE txlist card. Use one SaldoCard-list with TxnRows (5 most recent, any month), hairlines.
   Remove the month switcher from Home entirely (web home = current month fixed).

### Transaksi
1. TopBar "Transaksi" + right sub "{n} catatan" (count of filtered list).
2. SearchBar (binds existing search query state).
3. Filter chips row (wrap): Semua / Pengeluaran / Pemasukan / Transfer + active-category chip (name + ✕, ink style) when drill/category filter set + active-account chip similarly. Keep existing filter state/logic (type/account/category/search + batch selection). Chips must reflect & control existing filter state.
4. List: grouped by day; day label row (formatted day: "Hari ini"/"Kemarin"/"Sen, 12 Okt 2026" uppercase style + right net for the day "+Rp…/−Rp…" — omit net when 0), then a SaldoCard-list of that day's TxnRows. Keep long-press batch select + batch delete UI (restyle to tokens; selection highlight = accent @8%).
5. Empty state card per component spec ("Tidak ada transaksi" / "Coba ubah kata kunci atau filter yang aktif.").

### Statistik
1. TopBar "Statistik".
2. SegControl Minggu/Bulan/Tahun (existing period state).
3. Month nav row: iconbtn chevron-left, centered bold 14.5 month label (capitalize), iconbtn chevron-right. (Keep native period behavior; for Minggu/Tahun keep native nav semantics.)
4. SectionTitle "Skor kesehatan keuangan" + span "baru" → **Health card** (NEW, §6).
5. SectionTitle "Pengeluaran per kategori" → donut card (same as Home donut, for selected period; center "Total keluar").
6. SectionTitle "Arus kas 6 bulan"
...[truncated 8945 chars]