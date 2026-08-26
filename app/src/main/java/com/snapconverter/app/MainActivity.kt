package com.snapconverter.app

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
    }
}
