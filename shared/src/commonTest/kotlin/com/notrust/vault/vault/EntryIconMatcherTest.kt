package com.notrust.vault.vault

import kotlin.test.Test
import kotlin.test.assertEquals

class EntryIconMatcherTest {

    @Test
    fun matchesCommonServicesByDomainKeyword() {
        assertEquals(EntryIconCategory.SOCIAL, EntryIconMatcher.categoryFor("instagram.com"))
        assertEquals(EntryIconCategory.SOCIAL, EntryIconMatcher.categoryFor("www.facebook.com"))
        assertEquals(EntryIconCategory.EMAIL, EntryIconMatcher.categoryFor("mail.google.com"))
        assertEquals(EntryIconCategory.MESSAGING, EntryIconMatcher.categoryFor("web.whatsapp.com"))
        assertEquals(EntryIconCategory.BANKING, EntryIconMatcher.categoryFor("chase.com"))
        assertEquals(EntryIconCategory.SHOPPING, EntryIconMatcher.categoryFor("amazon.com"))
        assertEquals(EntryIconCategory.STREAMING, EntryIconMatcher.categoryFor("netflix.com"))
        assertEquals(EntryIconCategory.DEV, EntryIconMatcher.categoryFor("github.com"))
    }

    @Test
    fun isCaseInsensitive() {
        assertEquals(EntryIconCategory.SOCIAL, EntryIconMatcher.categoryFor("INSTAGRAM.COM"))
    }

    @Test
    fun bareGoogleDefaultsToEmail() {
        assertEquals(EntryIconCategory.EMAIL, EntryIconMatcher.categoryFor("google.com"))
        assertEquals(EntryIconCategory.EMAIL, EntryIconMatcher.categoryFor("accounts.google.com"))
    }

    @Test
    fun googleDriveIsClassifiedAsCloudNotEmail() {
        // More specific match ("google drive") must win over the bare "google" catch-all.
        assertEquals(EntryIconCategory.CLOUD, EntryIconMatcher.categoryFor("Google Drive"))
    }

    @Test
    fun fallsBackToTagsWhenNameDoesNotMatch() {
        assertEquals(EntryIconCategory.BANKING, EntryIconMatcher.categoryFor("mycreditunion.example", tags = listOf("Banking")))
        assertEquals(EntryIconCategory.SOCIAL, EntryIconMatcher.categoryFor("some-forum.example", tags = listOf("Social Media")))
    }

    @Test
    fun unknownSiteWithNoTagsIsGeneric() {
        assertEquals(EntryIconCategory.GENERIC, EntryIconMatcher.categoryFor("my-private-notes.example"))
    }

    @Test
    fun resolve_prefersExplicitOverride() {
        assertEquals(
            EntryIconCategory.DEV,
            EntryIconMatcher.resolve("instagram.com", tags = emptyList(), override = "DEV")
        )
    }

    @Test
    fun resolve_fallsBackWhenOverrideIsInvalid() {
        assertEquals(
            EntryIconCategory.SOCIAL,
            EntryIconMatcher.resolve("instagram.com", tags = emptyList(), override = "NOT_A_REAL_CATEGORY")
        )
    }

    @Test
    fun resolve_fallsBackWhenNoOverrideGiven() {
        assertEquals(
            EntryIconCategory.SOCIAL,
            EntryIconMatcher.resolve("instagram.com", tags = emptyList(), override = null)
        )
    }
}
