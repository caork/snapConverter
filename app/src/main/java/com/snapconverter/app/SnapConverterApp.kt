package com.snapconverter.app

import android.app.Application
import com.snapconverter.engine.CompressionEngine

class SnapConverterApp : Application() {
    lateinit var engine: CompressionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = CompressionEngine(this)
    }
}
