package com.notrust.vault.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.notrust.vault.vault.EntryIconCategory
import com.notrust.vault.vault.EntryIconMatcher

/**
 * Offline, deterministic entry icon — a fixed icon+color per category
 * (see EntryIconMatcher in shared), or a colored monogram (first letter
 * of the alias, color hashed from the site name) when nothing matches.
 * No brand logos, no network — see EntryIconMatcher's class doc for why.
 */
internal data class IconStyle(val icon: ImageVector, val color: Color)

// Internal (not private) so the manual override picker in AddEditEntryScreen
// can reuse the exact same icon+color per category — the picker and the
// badge must never disagree about what a category looks like.
internal val CATEGORY_STYLES = mapOf(
    EntryIconCategory.BANKING to IconStyle(Icons.Default.AccountBalance, Color(0xFF2EE6C4)),
    EntryIconCategory.SOCIAL to IconStyle(Icons.Default.Diversity3, Color(0xFF7C6CFF)),
    EntryIconCategory.EMAIL to IconStyle(Icons.Default.Email, Color(0xFF4C9AFF)),
    EntryIconCategory.MESSAGING to IconStyle(Icons.Default.Chat, Color(0xFF34D399)),
    EntryIconCategory.SHOPPING to IconStyle(Icons.Default.ShoppingCart, Color(0xFFFFA94D)),
    EntryIconCategory.STREAMING to IconStyle(Icons.Default.PlayCircle, Color(0xFFFF6B81)),
    EntryIconCategory.DEV to IconStyle(Icons.Default.Code, Color(0xFF9AA4B2)),
    EntryIconCategory.CLOUD to IconStyle(Icons.Default.Cloud, Color(0xFF5CC8FF))
)

private val MONOGRAM_PALETTE = listOf(
    Color(0xFF2EE6C4), Color(0xFF7C6CFF), Color(0xFF4C9AFF), Color(0xFFFFA94D),
    Color(0xFFFF6B81), Color(0xFF34D399), Color(0xFF9AA4B2), Color(0xFF5CC8FF)
)

@Composable
fun EntryIconBadge(
    siteName: String,
    alias: String,
    tags: List<String>,
    iconOverride: String?,
    size: Dp = 40.dp
) {
    // Resolved once — the icon glyph and its background color must always
    // agree, so both are derived from this single category, never
    // recomputed independently (a manual override that changed the icon
    // but not the color would look like a bug, because it would be one).
    val category = EntryIconMatcher.resolve(siteName, tags, iconOverride)
    val style = CATEGORY_STYLES[category]
    val backgroundColor = style?.color ?: hashedMonogramColor(siteName.ifEmpty { alias })

    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (style != null) {
            Icon(style.icon, contentDescription = category.name, tint = Color(0xFF04060A))
        } else {
            Text(monogramLetter(alias, siteName), color = Color(0xFF04060A), fontWeight = FontWeight.Bold)
        }
    }
}

private fun monogramLetter(alias: String, siteName: String): String {
    val source = alias.trim().ifEmpty { siteName.trim() }
    return source.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private fun hashedMonogramColor(seedText: String): Color {
    val seed = seedText.lowercase().trim().hashCode()
    val index = ((seed % MONOGRAM_PALETTE.size) + MONOGRAM_PALETTE.size) % MONOGRAM_PALETTE.size
    return MONOGRAM_PALETTE[index]
}
