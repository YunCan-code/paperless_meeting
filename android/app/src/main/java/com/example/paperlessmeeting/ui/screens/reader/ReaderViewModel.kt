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
    private val _annotations = MutableStateFlow<List<AnnotationLine>>(emptyList())
    val annotations: StateFlow<List<AnnotationLine>> = _annotations.asStateFlow()

    fun loadAnnotations(pdfFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonFile = File(pdfFile.parent, "${pdfFile.name}.json")
                if (jsonFile.exists()) {
                    val jsonString = jsonFile.readText()
                    val type = object : TypeToken<DocumentAnnotations>() {}.type
                    val data = Gson().fromJson<DocumentAnnotations>(jsonString, type)
                    // Ensure we only load lines compatible with current data model
                    _annotations.value = data.lines
                } else {
                    _annotations.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // In case of error (e.g. malformed JSON), assume empty
                _annotations.value = emptyList()
            }
        }
    }

    fun saveAnnotations(pdfFile: File) {
        val currentLines = _annotations.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If empty, maybe delete the file? Or just save empty.
                // Saving empty is safer to overwrite previous data.
                val data = DocumentAnnotations(pdfFile.name, currentLines)
                val jsonString = Gson().toJson(data)
                val jsonFile = File(pdfFile.parent, "${pdfFile.name}.json")
                jsonFile.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addAnnotation(line: AnnotationLine) {
        // Create new list to trigger flow emission
        _annotations.value = _annotations.value + line
    }

    fun undoAnnotation() {
        if (_annotations.value.isNotEmpty()) {
            _annotations.value = _annotations.value.dropLast(1)
        }
    }
}

