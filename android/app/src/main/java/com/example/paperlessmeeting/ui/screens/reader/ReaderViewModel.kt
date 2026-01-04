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
    data class Ready(val file: File) : ReaderUiState()
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
                
                _uiState.value = ReaderUiState.Ready(file)
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

