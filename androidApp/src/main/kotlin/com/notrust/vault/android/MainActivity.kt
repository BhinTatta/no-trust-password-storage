package com.notrust.vault.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.notrust.vault.android.ui.VaultApp

// FragmentActivity, not the more minimal ComponentActivity — BiometricPrompt
// requires one. setContent (Compose) still works: FragmentActivity extends
// ComponentActivity, and setContent is an extension on that base type.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots, screen recording, and the recents-list
        // thumbnail for the entire app lifetime — see docs/SECURITY.md.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val repository = VaultRepository(filesDir)
        val biometricKeyStore = AndroidBiometricKeyStore(this)
        setContent {
            VaultApp(repository, biometricKeyStore)
        }
    }
}
