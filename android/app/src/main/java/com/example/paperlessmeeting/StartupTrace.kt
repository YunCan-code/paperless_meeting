package com.example.paperlessmeeting

import android.os.SystemClock
import android.util.Log

object StartupTrace {
    private const val TAG = "StartupTrace"
    private val traceStartUptime = SystemClock.uptimeMillis()

    fun mark(stage: String) {
        val now = SystemClock.uptimeMillis()
        Log.d(TAG, "stage=$stage uptime=$now delta=${now - traceStartUptime}ms")
    }
}
