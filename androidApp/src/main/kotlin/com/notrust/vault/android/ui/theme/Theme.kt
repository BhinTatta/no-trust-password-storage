package com.notrust.vault.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Deliberately not the default Material dynamic-color theme — see
 * docs/UI_DESIGN.md. This is a cipher/cryptography identity, not a
 * generic dark app: a near-black void, one electric cyan signal color
 * that means "this is live cryptographic material" (unlock, reveal,
 * active state), and a violet used only for depth/atmosphere (the
 * cipher-rain background, subtle glows) — never both accents competing
 * for the same job. Deliberately not Matrix green: that's the cliché
 * this is trying to read as something more considered than.
 */
object VaultColors {
    val Void = Color(0xFF04060A)          // background — near-black, cold undertone
    val Depth = Color(0xFF0A0E16)         // just above Void, for large recessed areas
    val Surface = Color(0xFF11151F)       // cards, sheets
    val SurfaceRaised = Color(0xFF1A2030) // elevated surfaces, input fields
    val Hairline = Color(0xFF262E42)      // dividers, borders

    val Signal = Color(0xFF2EE6C4)        // the one accent that means "cryptographic action"
    val SignalDim = Color(0xFF1B8F7C)
    val Depth2 = Color(0xFF7C6CFF)        // atmosphere-only violet — never on interactive controls

    val TextPrimary = Color(0xFFEAF0F6)
    val TextMuted = Color(0xFF7C879A)
    val TextFaint = Color(0xFF4A5468)

    val Danger = Color(0xFFFF5C72)
}

private val VaultDarkColorScheme = darkColorScheme(
    background = VaultColors.Void,
    onBackground = VaultColors.TextPrimary,
    surface = VaultColors.Surface,
    onSurface = VaultColors.TextPrimary,
    surfaceVariant = VaultColors.SurfaceRaised,
    onSurfaceVariant = VaultColors.TextMuted,
    outline = VaultColors.Hairline,
    primary = VaultColors.Signal,
    onPrimary = Color(0xFF00201C),
    secondary = VaultColors.Depth2,
    onSecondary = Color(0xFF15102E),
    error = VaultColors.Danger,
    onError = Color(0xFF2A0008)
)

// A light scheme exists for completeness (system light-mode users), but
// this app's real identity is the dark one — see docs/UI_DESIGN.md.
private val VaultLightColorScheme = lightColorScheme(
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF10141C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10141C),
    surfaceVariant = Color(0xFFEBEDF2),
    onSurfaceVariant = Color(0xFF565F70),
    outline = Color(0xFFD8DBE3),
    primary = Color(0xFF0E9C86),
    onPrimary = Color.White,
    secondary = Color(0xFF5B4CD6),
    onSecondary = Color.White,
    error = Color(0xFFC7293E),
    onError = Color.White
)

/** Vault content — site names, usernames, passwords, aliases — always renders in this, never body text. */
val VaultMonoTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    letterSpacing = 0.2.sp
)

/**
 * Section labels ("SECURITY", "TAGS"), action-button text, and other small
 * meta text get a wide-tracked uppercase treatment — a console feel, kept
 * deliberately small and secondary. Never used for a screen's primary
 * title (see [VaultScreenTitleTextStyle]) — applying the same tiny tracked
 * caps to everything, headline included, is what read as a first pass
 * rather than a considered hierarchy.
 */
val VaultLabelTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.8.sp,
    fontSize = 12.sp
)

/**
 * A screen's primary title (top bar, headline) — clean sans, real weight,
 * sized to actually read as a title. Deliberately not the Signal accent
 * color: Signal means "live cryptographic material" elsewhere in this
 * app (unlock, reveal), and a title isn't that — using TextPrimary here
 * keeps that signal meaningful instead of diluting it into decoration.
 */
val VaultScreenTitleTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    letterSpacing = 0.1.sp
)

private val VaultTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.sp)
)

/** Rounded, generous corners for every field/card — the small default Material radius is a big part of what reads as "generic form" rather than "premium product." */
val VaultFieldShape = RoundedCornerShape(14.dp)

/**
 * Every text field in the app shares this. No visible border at rest —
 * fields read as soft, filled surfaces, the way most premium mobile
 * products treat inputs — and gain a clean signal-cyan outline only on
 * focus, which also doubles as feedback that this field is now "live."
 */
@Composable
fun vaultFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VaultColors.Signal,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedContainerColor = VaultColors.SurfaceRaised,
    unfocusedContainerColor = VaultColors.SurfaceRaised,
    disabledContainerColor = VaultColors.SurfaceRaised,
    cursorColor = VaultColors.Signal,
    focusedLabelColor = VaultColors.Signal,
    unfocusedLabelColor = VaultColors.TextMuted,
    focusedTextColor = VaultColors.TextPrimary,
    unfocusedTextColor = VaultColors.TextPrimary
)

@Composable
fun NoTrustVaultTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) VaultDarkColorScheme else VaultLightColorScheme,
        typography = VaultTypography,
        content = content
    )
}
