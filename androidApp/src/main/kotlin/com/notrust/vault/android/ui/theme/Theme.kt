package com.notrust.vault.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Deliberately not the default Material dynamic-color theme — see
 * docs/UI_DESIGN.md. One fixed palette, dark-first, with a single amber
 * accent reserved for the reveal/unlock action so it actually means
 * something rather than decorating every button on screen. Site names,
 * usernames, passwords, and aliases render in monospace throughout — this
 * is precise, technical data, not prose, and it visually separates "vault
 * content" from "app chrome" without needing a second custom typeface.
 */
private val VaultBackground = Color(0xFF0A0A0C)
private val VaultSurface = Color(0xFF17181C)
private val VaultSurfaceVariant = Color(0xFF1F2126)
private val VaultOnBackground = Color(0xFFEDEDED)
private val VaultOnSurfaceMuted = Color(0xFF9195A0)
private val VaultAccent = Color(0xFFE0A458)
private val VaultAccentOn = Color(0xFF1A1200)
private val VaultDanger = Color(0xFFE5484D)

private val VaultDarkColorScheme = darkColorScheme(
    background = VaultBackground,
    onBackground = VaultOnBackground,
    surface = VaultSurface,
    onSurface = VaultOnBackground,
    surfaceVariant = VaultSurfaceVariant,
    onSurfaceVariant = VaultOnSurfaceMuted,
    primary = VaultAccent,
    onPrimary = VaultAccentOn,
    error = VaultDanger,
    onError = Color(0xFF1A0000)
)

// A light scheme exists for completeness (system light-mode users), but
// this app's identity is the dark one — see docs/UI_DESIGN.md.
private val VaultLightColorScheme = lightColorScheme(
    background = Color(0xFFFAFAF9),
    onBackground = Color(0xFF16171A),
    surface = Color(0xFFF1F1EF),
    onSurface = Color(0xFF16171A),
    surfaceVariant = Color(0xFFE6E6E3),
    onSurfaceVariant = Color(0xFF5B5D66),
    primary = Color(0xFF9A6B2A),
    onPrimary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White
)

val VaultMonoTextStyle = TextStyle(fontFamily = FontFamily.Monospace)

private val VaultTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun NoTrustVaultTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme,
        typography = VaultTypography,
        content = content
    )
}
