package com.notrust.vault.android

import android.os.Build
import android.os.Debug
import java.io.File

/**
 * A best-effort, non-blocking heuristic — not a security boundary. It
 * exists to warn you, not to stop anyone: a real attacker with root can
 * defeat any on-device check like this one. See docs/SECURITY.md — a
 * fully compromised, unlocked device is explicitly out of scope. This is
 * only worth having because it catches the common, non-adversarial case
 * (you rooted your own phone and forgot) cheaply.
 */
object DeviceIntegrity {
    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/su/bin/su",
        "/system/app/Superuser.apk",
        "/system/app/Magisk"
    )

    fun looksCompromised(): Boolean {
        return hasSuBinary() || hasTestKeysBuild() || Debug.isDebuggerConnected()
    }

    private fun hasSuBinary(): Boolean = SU_PATHS.any { File(it).exists() }

    private fun hasTestKeysBuild(): Boolean = Build.TAGS?.contains("test-keys") == true
}
