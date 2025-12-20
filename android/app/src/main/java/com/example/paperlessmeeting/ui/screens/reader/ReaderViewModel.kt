package com.example.paperlessmeeting.ui.screens.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

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
}
