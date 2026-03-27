package com.example.paperlessmeeting.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

class DocumentThumbnailRepository private constructor(
    private val appContext: Context
) {

    private val diskCacheDir = File(appContext.cacheDir, "pdf_thumbs").apply { mkdirs() }
    private val renderSemaphore = Semaphore(2)
    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    suspend fun getPdfCover(filePath: String, widthPx: Int): Bitmap? {
        return getPdfPageThumbnail(filePath = filePath, pageIndex = 0, widthPx = widthPx)
    }

    suspend fun getPdfPageThumbnail(
        filePath: String,
        pageIndex: Int,
        widthPx: Int
    ): Bitmap? {
        return loadOrRender(
            filePath = filePath,
            pageIndex = pageIndex,
            widthPx = widthPx,
            sharedRenderer = null
        )
    }

    suspend fun getPdfPageThumbnail(
        filePath: String,
        pageIndex: Int,
        widthPx: Int,
        sharedRenderer: PdfRenderer
    ): Bitmap? {
        return loadOrRender(
            filePath = filePath,
            pageIndex = pageIndex,
            widthPx = widthPx,
            sharedRenderer = sharedRenderer
        )
    }

    private suspend fun loadOrRender(
        filePath: String,
        pageIndex: Int,
        widthPx: Int,
        sharedRenderer: PdfRenderer?
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return@withContext null
        }

        val safeWidth = widthPx.coerceAtLeast(96)
        val cacheKey = buildCacheKey(file, pageIndex, safeWidth)
        memoryCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return@withContext it }

        val diskFile = File(diskCacheDir, "${sha256(cacheKey)}.jpg")
        decodeDiskBitmap(diskFile)?.also {
            memoryCache.put(cacheKey, it)
            return@withContext it
        }

        renderSemaphore.withPermit {
            memoryCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return@withPermit it }
            decodeDiskBitmap(diskFile)?.also {
                memoryCache.put(cacheKey, it)
                return@withPermit it
            }

            val rendered = if (sharedRenderer != null) {
                renderWithSharedRenderer(sharedRenderer, pageIndex, safeWidth)
            } else {
                renderWithOwnedRenderer(file, pageIndex, safeWidth)
            }

            if (rendered != null) {
                writeBitmapToDisk(diskFile, rendered)
                memoryCache.put(cacheKey, rendered)
            }
            rendered
        }
    }

    private fun renderWithOwnedRenderer(
        file: File,
        pageIndex: Int,
        widthPx: Int
    ): Bitmap? {
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            renderPage(renderer, pageIndex, widthPx)
        } catch (_: Exception) {
            null
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                descriptor?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun renderWithSharedRenderer(
        renderer: PdfRenderer,
        pageIndex: Int,
        widthPx: Int
    ): Bitmap? {
        return synchronized(renderer) {
            try {
                renderPage(renderer, pageIndex, widthPx)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun renderPage(
        renderer: PdfRenderer,
        pageIndex: Int,
        widthPx: Int
    ): Bitmap? {
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
            return null
        }

        renderer.openPage(pageIndex).use { page ->
            val heightPx = max(1, (widthPx.toFloat() / page.width * page.height).toInt())
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    private fun decodeDiskBitmap(file: File): Bitmap? {
        if (!file.exists() || file.length() <= 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun writeBitmapToDisk(file: File, bitmap: Bitmap) {
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildCacheKey(file: File, pageIndex: Int, widthPx: Int): String {
        return "pdf:${file.absolutePath}:${file.lastModified()}:$pageIndex:$widthPx"
    }

    private fun memoryCacheSizeBytes(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory().toInt()
        return maxMemory / 12
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(value.toByteArray())
        return buildString(hash.size * 2) {
            hash.forEach { byte ->
                append("%02x".format(byte))
            }
        }
    }

    companion object {
        @Volatile
        private var instance: DocumentThumbnailRepository? = null

        fun getInstance(context: Context): DocumentThumbnailRepository {
            return instance ?: synchronized(this) {
                instance ?: DocumentThumbnailRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
