package com.example.paperlessmeeting.ui.screens.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

sealed class ReaderUiState {
    object Idle : ReaderUiState()
    object Loading : ReaderUiState()
    data class Downloading(val progress: Float, val fileName: String) : ReaderUiState()
    data class Ready(val file: File, val initialPage: Int = 0) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val readingProgressManager: com.example.paperlessmeeting.data.local.ReadingProgressManager,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        cleanupOldCache()
    }

    private fun cleanupOldCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val cacheDir = context.cacheDir
                val expirationTime = System.currentTimeMillis() - (15L * 24 * 3600 * 1000) // 15 days
                
                cacheDir.listFiles()?.forEach { file ->
                    // Iterate all files (pdfs, jsons, etc)
                    if (file.isFile && file.lastModified() < expirationTime) {
                         file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadDocument(url: String, fileName: String) {
        if (_uiState.value is ReaderUiState.Ready) return

        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val cacheDir = context.cacheDir
                
                // Use URL hash to generate a unique filename, preventing conflicts for same-named files in different meetings
                val extension = fileName.substringAfterLast(".", "pdf")
                val uniqueName = "${url.hashCode()}.$extension"
                val file = File(cacheDir, uniqueName)

                if (!file.exists()) {
                    // 使用带进度的下载，支持自动重试
                    val maxRetries = 3
                    var success = false
                    var lastError: String? = null
                    
                    for (attempt in 1..maxRetries) {
                        _uiState.value = ReaderUiState.Downloading(0f, fileName)
                        
                        success = repository.downloadFileWithProgress(url, file) { progress ->
                            _uiState.value = ReaderUiState.Downloading(progress, fileName)
                        }
                        
                        if (success) break
                        
                        // 下载失败，准备重试
                        if (attempt < maxRetries) {
                            lastError = "下载失败，正在重试 ($attempt/$maxRetries)..."
                            _uiState.value = ReaderUiState.Error(lastError)
                            kotlinx.coroutines.delay(2000) // 等待2秒后重试
                            // 删除可能的不完整文件
                            if (file.exists()) file.delete()
                        }
                    }
                    
                    if (!success) {
                        // 删除不完整的文件
                        if (file.exists()) file.delete()
                        _uiState.value = ReaderUiState.Error("下载失败，请检查网络连接后重试")
                        return@launch
                    }
                } else {
                    // Touch file to extend its retention period
                    val now = System.currentTimeMillis()
                    file.setLastModified(now)
                    // Also touch the annotation file if it exists
                    val jsonFile = File(file.parent, "${file.name}.json")
                    if (jsonFile.exists()) {
                        jsonFile.setLastModified(now)
                    }
                }
                
                // Fetch saved progress
                val savedProgress = readingProgressManager.getProgress(url)
                val initialPage = savedProgress?.currentPage ?: 0
                
                _uiState.value = ReaderUiState.Ready(file, initialPage)
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    // Annotation Logic
    // Start with a Map for O(1) access by page index
    private val _pageAnnotations = MutableStateFlow<Map<Int, List<AnnotationStroke>>>(emptyMap())
    val pageAnnotations: StateFlow<Map<Int, List<AnnotationStroke>>> = _pageAnnotations.asStateFlow()

    fun loadAnnotations(pdfFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonFile = File(pdfFile.parent, "${pdfFile.name}.json")
                if (jsonFile.exists()) {
                    val jsonString = jsonFile.readText()
                    // Define a DTO for storage to keep JSON clean
                    val type = object : TypeToken<Map<Int, List<AnnotationStroke>>>() {}.type
                    val data = Gson().fromJson<Map<Int, List<AnnotationStroke>>>(jsonString, type)
                    _pageAnnotations.value = data ?: emptyMap()
                } else {
                    _pageAnnotations.value = emptyMap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _pageAnnotations.value = emptyMap()
            }
        }
    }

    fun saveAnnotations(pdfFile: File) {
        val currentData = _pageAnnotations.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = Gson().toJson(currentData)
                val jsonFile = File(pdfFile.parent, "${pdfFile.name}.json")
                jsonFile.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updatePageAnnotations(pageIndex: Int, strokes: List<AnnotationStroke>) {
        val currentMap = _pageAnnotations.value.toMutableMap()
        if (strokes.isEmpty()) {
            currentMap.remove(pageIndex)
        } else {
            currentMap[pageIndex] = strokes
        }
        _pageAnnotations.value = currentMap
    }

    fun saveReadingProgress(uniqueId: String, fileName: String, page: Int, total: Int, localPath: String? = null) {
        viewModelScope.launch {
            if (total > 0) {
                readingProgressManager.saveProgress(uniqueId, fileName, page, total, localPath)
            }
        }
    }
}

