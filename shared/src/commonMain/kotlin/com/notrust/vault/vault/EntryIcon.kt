package com.notrust.vault.vault

/**
 * Auto-suggested icon category for a browse-tier entry, based only on
 * its site name and tags — both already low-stakes, non-secret data at
 * this tier, so this needs no master password and no network call.
 *
 * Deliberately not favicon-fetching: pinging a live icon service (Google
 * s2/favicons, Clearbit, etc.) with every site name in your vault would
 * leak exactly which services you have accounts with to a third party,
 * every time you open the app — the one thing this whole project exists
 * to avoid. Categories map to bundled Material icons + a fixed color per
 * category (see the Android `EntryIconBadge`), not real brand logos —
 * getting pixel-accurate Instagram/Facebook/bank marks would need an
 * actual licensed icon asset pack, not something to approximate from memory.
 */
enum class EntryIconCategory {
    BANKING, SOCIAL, EMAIL, MESSAGING, SHOPPING, STREAMING, DEV, CLOUD, GENERIC
}

object EntryIconMatcher {
    private val KEYWORDS: List<Pair<EntryIconCategory, List<String>>> = listOf(
        EntryIconCategory.EMAIL to listOf("gmail", "outlook", "yahoo mail", "protonmail", "mail.", "hotmail"),
        EntryIconCategory.SOCIAL to listOf(
            "instagram", "facebook", "twitter", "x.com", "tiktok", "snapchat", "pinterest", "reddit", "linkedin"
        ),
        EntryIconCategory.MESSAGING to listOf("whatsapp", "telegram", "signal", "discord", "slack", "messenger"),
        EntryIconCategory.BANKING to listOf(
            "bank", "chase", "hsbc", "hdfc", "icici", "sbi", "wells fargo", "citibank",
            "paypal", "visa", "mastercard", "axis", "kotak", "barclays"
        ),
        EntryIconCategory.SHOPPING to listOf("amazon", "flipkart", "ebay", "etsy", "walmart", "myntra"),
        EntryIconCategory.STREAMING to listOf("netflix", "spotify", "hulu", "disney", "prime video", "youtube", "hotstar"),
        EntryIconCategory.DEV to listOf("github", "gitlab", "bitbucket", "stackoverflow", "npm", "docker"),
        EntryIconCategory.CLOUD to listOf("google drive", "dropbox", "onedrive", "icloud"),
        // Bare "google" (not gmail/drive, already matched above) defaults to email —
        // the overwhelmingly common reason to save a standalone Google login.
        EntryIconCategory.EMAIL to listOf("google")
    )

    fun categoryFor(siteName: String, tags: List<String> = emptyList()): EntryIconCategory {
        val haystack = siteName.lowercase()
        for ((category, keywords) in KEYWORDS) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        for (tag in tags) {
            when (tag.trim().lowercase()) {
                "banking" -> return EntryIconCategory.BANKING
                "social media", "social" -> return EntryIconCategory.SOCIAL
                "work", "google" -> return EntryIconCategory.EMAIL
            }
        }
        return EntryIconCategory.GENERIC
    }

    /** Resolves an explicit user override first, falling back to auto-matching. */
    fun resolve(siteName: String, tags: List<String>, override: String?): EntryIconCategory {
        override?.let { name ->
            EntryIconCategory.entries.firstOrNull { it.name == name }?.let { return it }
        }
        return categoryFor(siteName, tags)
    }
}
