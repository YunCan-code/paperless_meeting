package com.example.paperlessmeeting.ui.screens.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class ReaderUiState {
    object Idle : ReaderUiState()
    object Loading : ReaderUiState()
    data class Ready(val file: File, val pageCount: Int) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: MeetingRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    fun loadDocument(url: String, fileName: String) {
        if (_uiState.value is ReaderUiState.Ready) return 

        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val cacheDir = context.cacheDir
                val file = File(cacheDir, fileName)

                if (!file.exists()) {
                    val success = repository.downloadFile(url, file)
                    if (!success) {
                        _uiState.value = ReaderUiState.Error("Download failed")
                        return@launch
                    }
                }

                initRenderer(file)
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    private suspend fun initRenderer(file: File) {
        withContext(Dispatchers.IO) {
            try {
                fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)
                _uiState.value = ReaderUiState.Ready(file, pdfRenderer!!.pageCount)
            } catch (e: Exception) {
                 _uiState.value = ReaderUiState.Error("Invalid PDF: ${e.message}")
            }
        }
    }

    fun renderPage(index: Int, reqWidth: Int, reqHeight: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (index < 0 || index >= renderer.pageCount) return null
        
        return try {
            val page = renderer.openPage(index)
            
            // Calculate aspect ratio to fit the screen without stretching
            val scale = minOf(reqWidth.toFloat() / page.width, reqHeight.toFloat() / page.height)
            val bitmapWidth = (page.width * scale).toInt().coerceAtLeast(1)
            val bitmapHeight = (page.height * scale).toInt().coerceAtLeast(1)
            
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            // Fix: Fill background with white
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            pdfRenderer?.close()
            fileDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
