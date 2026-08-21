package com.notrust.vault.model

import kotlinx.serialization.Serializable

/**
 * The "browse tier" — everything visible after a biometric unlock alone.
 * Deliberately minimal: no username, no password, nothing that would
 * matter if this list leaked from a stolen-but-biometrically-unlocked phone.
 */
@Serializable
data class BrowseIndexItem(
    val id: String,
    val alias: String,
    val siteName: String,
    // Tags ("Banking", "Work", custom labels) are organizational, not
    // secret — same trust tier as alias/siteName, so they live here
    // rather than needing a master-password reveal just to filter a list.
    val tags: List<String> = emptyList(),
    // A user-chosen EntryIconCategory name, or null to auto-detect from
    // siteName/tags (see EntryIconMatcher). Just a display label — no
    // more sensitive than the alias it sits next to.
    val iconOverride: String? = null
)

/**
 * The "secrets tier" — only ever exists in plaintext transiently, in
 * memory, immediately after a master-password reveal. Never cached,
 * never logged, never part of the browse index.
 */
@Serializable
data class EntrySecrets(
    val username: String,
    val password: String,
    val notes: String = "",
    val totpSeed: String? = null
)
