package com.snapconverter.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.snapconverter.app.ui.JobViewModel
import com.snapconverter.app.ui.ScanViewModel
import com.snapconverter.app.ui.screens.HomeScreen
import com.snapconverter.app.ui.screens.ScanScreen
import com.snapconverter.app.ui.settings.AppSettings
import com.snapconverter.app.ui.theme.SnapConverterTheme

/** The app has two destinations: the single-file workbench and the library scan. */
private enum class Route { Home, Scan }

class MainActivity : ComponentActivity() {
    private val viewModel: JobViewModel by viewModels()
    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var settings by remember { mutableStateOf(AppSettings.load(this@MainActivity)) }
            var route by remember { mutableStateOf(Route.Home) }
            SnapConverterTheme(
                themeMode = settings.themeMode,
                oledBlack = settings.oledBlack,
                glassPreset = settings.glassPreset,
            ) {
                when (route) {
                    Route.Home -> HomeScreen(
                        viewModel = viewModel,
                        settings = settings,
                        onSettingsChange = { settings = it },
                        onOpenScan = { route = Route.Scan },
                    )
                    Route.Scan -> {
                        BackHandler { route = Route.Home }
                        ScanScreen(
                            viewModel = scanViewModel,
                            onBack = { route = Route.Home },
                        )
                    }
                }
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
