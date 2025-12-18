package com.example.paperlessmeeting

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaperlessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize other libraries here (e.g. Timber)
    }
}
