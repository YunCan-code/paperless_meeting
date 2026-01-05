package com.example.paperlessmeeting.worker

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.paperlessmeeting.data.repository.DeviceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val deviceRepository: DeviceRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_COMMAND_ID = "command_id"
    }

    override suspend fun doWork(): Result {
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return Result.failure()
        val commandId = inputData.getInt(KEY_COMMAND_ID, -1)

        Log.d("UpdateWorker", "Starting APK download: $downloadUrl")

        return try {
            // Download APK
            val responseResult = deviceRepository.downloadApk(downloadUrl)
            if (responseResult.isFailure) {
                Log.e("UpdateWorker", "Download failed: ${responseResult.exceptionOrNull()}")
                return Result.retry()
            }

            val responseBody = responseResult.getOrNull()!!
            val apkFile = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk")

            // Write to file
            responseBody.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d("UpdateWorker", "APK downloaded to: ${apkFile.absolutePath}")

            // Ack command
            if (commandId > 0) {
                deviceRepository.ackCommand(commandId)
            }

            // Trigger install
            installApk(apkFile)

            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Update failed", e)
            Result.retry()
        }
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        appContext.startActivity(intent)
    }
}
