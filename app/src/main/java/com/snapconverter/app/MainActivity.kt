package com.snapconverter.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.screens.HomeScreen
import com.snapconverter.app.ui.theme.SnapConverterTheme
import com.snapconverter.engine.policy.MediaKind

class MainActivity : ComponentActivity() {
    private val viewModel: JobViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SnapConverterTheme {
                HomeScreen(viewModel)
            }
        }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent) {
        val uri: Uri = incomingUri(intent) ?: return
        val mime = intent.type ?: contentResolver.getType(uri).orEmpty()
        val kind = if (mime.startsWith("image/")) MediaKind.IMAGE else MediaKind.VIDEO
        val autostart = intent.getBooleanExtra(EXTRA_AUTOSTART, false)
        viewModel.onPicked(uri, kind, autostart)
    }

    private fun incomingUri(intent: Intent): Uri? {
        if (intent.action == Intent.ACTION_SEND) {
            return if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        }
        return intent.data
    }

    companion object {
        const val EXTRA_AUTOSTART = "com.snapconverter.extra.AUTOSTART"
    }
}
