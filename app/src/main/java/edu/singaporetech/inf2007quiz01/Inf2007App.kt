package edu.singaporetech.inf2007quiz01

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with @HiltAndroidApp for Hilt dependency injection.
 */
@HiltAndroidApp
class Inf2007App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the .NET Mono runtime bridge.
        // This loads libmonosgen-2.0.so and inventories the C#/F#/VB.NET
        // assemblies shipped in this calculator's APK.
        DotNetBridge.initialize(this)
    }
}
