package com.example.paperlessmeeting.ui.screens.dashboard

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

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(
        val meetings: List<Meeting>, 
        val activeMeetings: List<Meeting>,
        val recentFiles: List<com.example.paperlessmeeting.domain.model.Attachment>,
        val readingProgress: List<com.example.paperlessmeeting.data.local.ReadingProgress> = emptyList(),
        val initialPageIndex: Int = 0,
        val userName: String
    ) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    private val readingProgressManager: com.example.paperlessmeeting.data.local.ReadingProgressManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // Get user name
                val userName = userPreferences.getUserName() ?: "用户"

                val rawMeetings = repository.getMeetings()
                
                // 1. Parse and Sort by Start Time (Ascending)
                val now = java.time.LocalDateTime.now()
                val today = java.time.LocalDate.now()
                
                val sortedMeetings = rawMeetings.mapNotNull { meeting ->
                    try {
                        // Standardize format: "2023-12-19 09:00:00" -> "2023-12-19T09:00:00"
                        val isoTime = meeting.startTime.replace(" ", "T")
                        val time = java.time.LocalDateTime.parse(isoTime)
                        
                        // Filter: ONLY show meetings for Today
                        if (!time.toLocalDate().isEqual(today)) {
                            null
                        } else {
                            Pair(meeting, time)
                        }
                    } catch (e: Exception) {
                        null // Skip invalid date meetings
                    }
                }.sortedBy { it.second }

                // 2. Determine "Focus" Index based on 15-min Logic
                var startIndex = 0
                for (i in 0 until sortedMeetings.size - 1) {
                    val nextMeetingTime = sortedMeetings[i + 1].second
                    val switchTime = nextMeetingTime.minusMinutes(15)
                    if (now.isAfter(switchTime)) {
                        startIndex = i + 1
                    } else {
                        break
                    }
                }
                
                // Active list is ALL of today's meetings (sorted)
                val activeList = sortedMeetings.map { it.first }

                // Flatten and grab recent files (Legacy logic, maybe replace with progress logic later)
                val allFiles = rawMeetings.flatMap { it.attachments.orEmpty() }.take(10)
                
                // Load Reading Progress
                val progressList = readingProgressManager.getAllProgress()

                _uiState.value = DashboardUiState.Success(
                    meetings = rawMeetings, 
                    activeMeetings = activeList, 
                    recentFiles = allFiles,
                    readingProgress = progressList,
                    initialPageIndex = 0,
                    userName = userName
                )
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
