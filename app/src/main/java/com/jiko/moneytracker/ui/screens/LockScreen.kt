package com.jiko.moneytracker.ui.screens

import android.content.DialogInterface
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.MoneyViewModel
import com.jiko.moneytracker.ui.theme.Jakarta
import com.jiko.moneytracker.ui.theme.saldoColors
import java.util.concurrent.Executors

/** PIN + biometric gate shown on launch when lock is enabled.
 *  Biometric uses the framework API (29+); older devices fall back to PIN. */
@Composable
fun LockScreen(vm: MoneyViewModel, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    val bioAvailable = remember {
        Build.VERSION.SDK_INT >= 29 &&
                context.getSystemService(BiometricManager::class.java)
                    ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun tryBio() {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val executor = Executors.newSingleThreadExecutor()
            val prompt = BiometricPrompt.Builder(context)
                .setTitle("Buka Saldo")
                .setSubtitle("Verifikasi biometrik")
                .setNegativeButton("Pakai PIN", executor,
                    DialogInterface.OnClickListener { _, _ -> })
                .build()
            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                }
            )
        } catch (_: Exception) {
            // no enrolled biometrics / no device credential — stay on PIN pad
        }
    }

    LaunchedEffect(Unit) {
        if (vm.prefs.biometricEnabled && bioAvailable) tryBio()
    }

    fun submit() {
        if (vm.prefs.verifyPin(pin)) onUnlocked()
        else { err = "PIN salah"; pin = "" }
    }

    val pal = saldoColors
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(30.dp), tint = pal.accent)
        }
        Spacer(Modifier.height(16.dp))
        Text("Saldo Terkunci", fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp, color = pal.ink)
        Spacer(Modifier.height(6.dp))
        Text("Masukkan PIN untuk membuka Saldo.",
            fontFamily = Jakarta, fontSize = 13.sp, color = pal.muted)
        Spacer(Modifier.height(24.dp))
        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier.size(16.dp).clip(CircleShape).background(
                        if (i < pin.length) pal.accent
                        else pal.surface3
                    )
                )
            }
        }
        if (err.isNotEmpty()) {
            Text(err, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        // PIN pad
        listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("", "0", "⌫")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(68.dp).clip(CircleShape)
                            .shadow(2.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                when (k) {
                                    "⌫" -> pin = pin.dropLast(1)
                                    "" -> {}
                                    else -> {
                                        pin = (pin + k).take(6)
                                        if (pin.length == 6) submit()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (k == "⌫") Icon(Icons.Filled.Backspace, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (k.isNotEmpty()) Text(k, fontSize = 24.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        if (vm.prefs.biometricEnabled && bioAvailable) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { tryBio() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Icon(Icons.Filled.Fingerprint, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Biometrik")
            }
        }
    }
}
