package com.example.paperlessmeeting

import android.app.Application
import android.app.ActivityManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.paperlessmeeting.worker.HeartbeatWorker
import com.example.paperlessmeeting.worker.ForegroundHeartbeatManager
import dagger.hilt.android.HiltAndroidApp
import dagger.Lazy
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
    
    @Inject lateinit var okHttpClient: Lazy<OkHttpClient>

    @Inject lateinit var foregroundHeartbeatManager: Lazy<ForegroundHeartbeatManager>

    @Volatile
    private var backgroundServicesStarted = false

    @Volatile
    private var initialHeartbeatEnqueued = false

    override fun newImageLoader(): ImageLoader {
        StartupTrace.mark("PaperlessApp.newImageLoader.begin")
        val memoryCachePercent = if (isLowRamDevice()) 0.15 else 0.25
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient.get())
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
        StartupTrace.mark("PaperlessApp.newImageLoader.end")
        return imageLoader
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
        StartupTrace.mark("PaperlessApp.onCreate")
    }

    fun ensureDeferredStartupTasksStarted() {
        if (backgroundServicesStarted && initialHeartbeatEnqueued) return
        synchronized(this) {
            if (backgroundServicesStarted && initialHeartbeatEnqueued) return
            StartupTrace.mark("PaperlessApp.deferred_startup.begin")
            if (!backgroundServicesStarted) {
                foregroundHeartbeatManager.get().register()

                val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "HeartbeatWork",
                    ExistingPeriodicWorkPolicy.KEEP,
                    heartbeatRequest
                )
                backgroundServicesStarted = true
            }

            if (!initialHeartbeatEnqueued) {
                val initialHeartbeatRequest = OneTimeWorkRequestBuilder<HeartbeatWorker>()
                    .build()
                WorkManager.getInstance(this).enqueue(initialHeartbeatRequest)
                initialHeartbeatEnqueued = true
            }
            StartupTrace.mark("PaperlessApp.deferred_startup.end")
        }
    }
}
