package com.jiko.moneytracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

fun iconFor(key: String): ImageVector = when (key) {
    "food" -> Icons.Filled.Restaurant
    "car" -> Icons.Filled.DirectionsCar
    "bag" -> Icons.Filled.ShoppingBag
    "bill" -> Icons.Filled.Receipt
    "fun" -> Icons.Filled.Movie
    "health" -> Icons.Filled.MedicalServices
    "edu" -> Icons.Filled.School
    "spa" -> Icons.Filled.Spa
    "family" -> Icons.Filled.FamilyRestroom
    "work" -> Icons.Filled.Work
    "gift" -> Icons.Filled.CardGiftcard
    "shop" -> Icons.Filled.Store
    "chart" -> Icons.Filled.BarChart
    "trend" -> Icons.Filled.TrendingUp
    "heart" -> Icons.Filled.Favorite
    "spark" -> Icons.Filled.AutoAwesome
    "tag" -> Icons.Filled.LocalOffer
    "target" -> Icons.Filled.TrackChanges
    "bell" -> Icons.Filled.Notifications
    "cal" -> Icons.Filled.CalendarMonth
    "camera" -> Icons.Filled.PhotoCamera
    "image" -> Icons.Filled.Image
    "wallet" -> Icons.Filled.Wallet
    "bank" -> Icons.Filled.AccountBalance
    "phone" -> Icons.Filled.Smartphone
    "cash" -> Icons.Filled.Payments
    "card" -> Icons.Filled.CreditCard
    "home" -> Icons.Filled.Home
    "car2" -> Icons.Filled.AirportShuttle
    "pet" -> Icons.Filled.Pets
    "sport" -> Icons.Filled.FitnessCenter
    "book" -> Icons.Filled.Book
    "music" -> Icons.Filled.MusicNote
    "travel" -> Icons.Filled.Flight
    "coffee" -> Icons.Filled.Coffee
    "doc" -> Icons.Filled.Description
    "save" -> Icons.Filled.Savings
    "swap" -> Icons.Filled.SwapHoriz
    "autorenew" -> Icons.Filled.Autorenew
    else -> Icons.Filled.MoreHoriz
}

/** Icon choices in the Kelola Kategori editor — the web version's 27-icon vocabulary. */
val CATEGORY_ICON_CHOICES = listOf(
    "food", "car", "bag", "bill", "fun", "health", "edu", "spa", "family",
    "work", "gift", "shop", "wallet", "bank", "phone", "chart", "heart",
    "spark", "tag", "target", "bell", "cal", "camera", "image", "trend",
    "doc", "more"
)

/** Color palette in the Kelola Kategori editor — the web version's 11 colors. */
val CATEGORY_COLOR_CHOICES = listOf(
    "#FF5722", "#FF9100", "#D9A404", "#00C853", "#00BFA5", "#00B0FF",
    "#3D5AFE", "#7C4DFF", "#D6339E", "#FF4081", "#9E9E9E"
)

fun colorOf(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color(0xFF9E9E9E)
}

/** Category palette cycled for chart segments. */
val CHART_PALETTE = listOf(
    Color(0xFFFF5722), Color(0xFF7C4DFF), Color(0xFF00B0FF), Color(0xFF00C853),
    Color(0xFFFFD740), Color(0xFFE040FB), Color(0xFFFF9100), Color(0xFF00BFA5),
    Color(0xFF3D5AFE), Color(0xFFFF4081), Color(0xFF8D6E63), Color(0xFF78909C),
)
