package com.notrust.vault.vault

/**
 * Turns raw OCR text (from a photographed card/note/router label) into
 * *suggested* field values — never auto-committed. See docs/ROADMAP.md,
 * "OCR quick-add": the camera capture and text recognition itself is
 * platform-native (ML Kit on Android) and lives in the app layer once
 * Android is wired in; this is the shared, platform-agnostic "what to do
 * with the extracted text" half, which needs no camera or ML Kit
 * dependency to write or test.
 */
data class OcrGuess(
    val siteName: String?,
    val username: String?,
    val password: String?,
    val notes: String
)

object OcrFieldGuesser {
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val DOMAIN_REGEX = Regex("^(https?://)?([\\w-]+\\.)+[a-zA-Z]{2,}(/\\S*)?$")

    fun guess(rawText: String): OcrGuess {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val username = lines.firstOrNull { EMAIL_REGEX.matches(it) }
        // Deliberately no "just pick any other line" fallback here: with
        // only two lines (say, an email and a password, no URL at all),
        // a greedy fallback would misclassify the password as the site
        // name and the real password would never get detected at all.
        // Leaving siteName null when nothing looks like a domain is the
        // safer wrong guess — the user fills it in either way.
        val siteName = lines.firstOrNull { it != username && DOMAIN_REGEX.matches(it) }

        val used = setOfNotNull(username, siteName)
        val remaining = lines.filter { it !in used }
        val password = remaining.firstOrNull { looksLikePassword(it) }

        val notes = remaining.filter { it != password }.joinToString("\n")

        return OcrGuess(siteName = siteName, username = username, password = password, notes = notes)
    }

    /**
     * A password-shaped line: no spaces, reasonably long, and mixing at
     * least two of {letters, digits, symbols} — enough to tell "a
     * generated password" apart from an ordinary word or short label
     * without being so strict it misses real passwords. This is a guess,
     * always shown to the user for confirmation before saving — it never
     * needs to be perfect.
     */
    private fun looksLikePassword(line: String): Boolean {
        if (line.contains(' ') || line.length < 8) return false
        val hasLetter = line.any { it.isLetter() }
        val hasDigit = line.any { it.isDigit() }
        val hasSymbol = line.any { !it.isLetterOrDigit() }
        return listOf(hasLetter, hasDigit, hasSymbol).count { it } >= 2
    }
}
