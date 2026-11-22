package com.example

import android.app.Application
import com.example.diagnostics.CrashReporter

class MeteotabloApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    CrashReporter.initialize(this)
  }
}
