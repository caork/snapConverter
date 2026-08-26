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
        val uri = incomingUri(intent) ?: return
        val mime = intent.type ?: contentResolver.getType(uri).orEmpty()
        val name = uri.lastPathSegment.orEmpty()
        val kind = JobViewModel.detectKind(mime, name)
        val autostart = intent.getBooleanExtra(EXTRA_AUTOSTART, false)
        viewModel.onPicked(uri, kind, autostart)
    }

    private fun incomingUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_SEND -> extraStream(intent)
            Intent.ACTION_SEND_MULTIPLE -> extraStreamList(intent)?.firstOrNull()
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            else -> intent.data
        }
    }

    private fun extraStream(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun extraStreamList(intent: Intent): List<Uri>? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
    }

    companion object {
        const val EXTRA_AUTOSTART = "com.snapconverter.extra.AUTOSTART"
    }
}
