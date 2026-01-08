package com.example.paperlessmeeting.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MeetingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val meetingId: String? = savedStateHandle["meetingId"]
    
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadMeeting()
    }

    private fun loadMeeting() {
        viewModelScope.launch {
            try {
                val id = meetingId?.toIntOrNull()
                if (id == null) {
                    _uiState.value = DetailUiState.Error("Invalid Meeting ID")
                    return@launch
                }
                val meeting = repository.getMeetingById(id)
                if (meeting != null) {
                    _uiState.value = DetailUiState.Success(meeting)
                } else {
                    _uiState.value = DetailUiState.Error("Meeting not found")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = DetailUiState.Error("Error: ${e.message}")
            }
        }
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val meeting: Meeting) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
