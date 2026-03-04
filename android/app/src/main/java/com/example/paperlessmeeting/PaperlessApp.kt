package com.example.paperlessmeeting

import android.app.Application
import android.app.ActivityManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.paperlessmeeting.worker.HeartbeatWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

@HiltAndroidApp
class PaperlessApp : Application(), Configuration.Provider, ImageLoaderFactory {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory
    
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader {
        val memoryCachePercent = if (isLowRamDevice()) 0.15 else 0.25
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024L * 1024L)
                    .build()
            }
            .build()
    }

    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.isLowRamDevice ?: false
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Schedule Heartbeat (Every 15 minutes is minimum by default, but for demo we set 15)
        // If we need faster, we might need a foreground service or just accept 15 min for background.
        // For meeting tablet, maybe foreground service is better? But let's start with WorkManager.
        val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "HeartbeatWork",
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatRequest
        )
    }
}
