package com.notrust.vault.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OcrFieldGuesserTest {

    @Test
    fun guessesSiteUsernameAndPasswordFromATypicalCardPhoto() {
        val text = """
            netflix.com
            kartik@example.com
            Tr0ub4dor&3xyz
        """.trimIndent()

        val guess = OcrFieldGuesser.guess(text)

        assertEquals("netflix.com", guess.siteName)
        assertEquals("kartik@example.com", guess.username)
        assertEquals("Tr0ub4dor&3xyz", guess.password)
    }

    @Test
    fun handlesAUrlWithScheme() {
        val guess = OcrFieldGuesser.guess("https://accounts.example.com/login\nuser@example.com")
        assertEquals("https://accounts.example.com/login", guess.siteName)
        assertEquals("user@example.com", guess.username)
    }

    @Test
    fun leavesUnmatchedTextAsNotes() {
        val text = "Wifi Router\nnetgear.local\nadmin@router\nSuperSecret123!\nRoom 4B closet"
        val guess = OcrFieldGuesser.guess(text)

        assertTrue(guess.notes.contains("Wifi Router"))
        assertTrue(guess.notes.contains("Room 4B closet"))
    }

    @Test
    fun ordinaryShortWordsAreNotMistakenForPasswords() {
        val guess = OcrFieldGuesser.guess("just a note\nno secrets here")
        assertNull(guess.password)
    }

    @Test
    fun blankInputProducesAllNullsAndEmptyNotes() {
        val guess = OcrFieldGuesser.guess("   \n\n  ")
        assertNull(guess.siteName)
        assertNull(guess.username)
        assertNull(guess.password)
        assertEquals("", guess.notes)
    }

    @Test
    fun passwordDetectionRequiresAtLeastTwoCharacterClasses() {
        assertTrue(OcrFieldGuesser.guess("abcdefghij").password == null) // letters only, no digits/symbols
        assertTrue(OcrFieldGuesser.guess("abcdef1234").password == "abcdef1234") // letters + digits
    }

    @Test
    fun doesNotDoubleCountTheUsernameLineAsTheSiteName() {
        // Only one line total, and it's an email — must not appear as both.
        val guess = OcrFieldGuesser.guess("someone@example.com")
        assertEquals("someone@example.com", guess.username)
        assertNull(guess.siteName)
    }
}
