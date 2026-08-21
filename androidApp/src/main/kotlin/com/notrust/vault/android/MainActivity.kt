package com.notrust.vault.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.notrust.vault.android.ui.VaultApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block screenshots, screen recording, and the recents-list
        // thumbnail for the entire app lifetime — see docs/SECURITY.md.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val repository = VaultRepository(filesDir)
        setContent {
            VaultApp(repository)
        }
    }
}
