package com.example.paperlessmeeting.ui.screens.home

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
class HomeViewModel @Inject constructor(
    private val repository: MeetingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedMeetingId = MutableStateFlow<Int?>(null)
    val selectedMeetingId: StateFlow<Int?> = _selectedMeetingId.asStateFlow()

    fun selectMeeting(id: Int?) {
        _selectedMeetingId.value = id
    }
    
    // Pagination state
    private var currentPage = 0
    private val pageSize = 20
    private var hasMoreData = true
    private var isLoadingMore = false
    private val allMeetings = mutableListOf<Meeting>()

    init {
        loadMeetings()
    }

    /**
     * 会议排序：今天 -> 未来 -> 历史
     */
    private fun sortMeetingsWithTodayFirst(meetings: List<Meeting>): List<Meeting> {
        val today = java.time.LocalDate.now()
        
        val (todayMeetings, otherMeetings) = meetings.partition { meeting ->
            try {
                val dateStr = meeting.startTime.substringBefore(" ")
                val meetingDate = java.time.LocalDate.parse(dateStr)
                meetingDate == today
            } catch (e: Exception) { false }
        }
        
        val (futureMeetings, pastMeetings) = otherMeetings.partition { meeting ->
            try {
                val dateStr = meeting.startTime.substringBefore(" ")
                val meetingDate = java.time.LocalDate.parse(dateStr)
                meetingDate.isAfter(today)
            } catch (e: Exception) { false }
        }
        
        // 今日(时间升序) + 未来(日期升序) + 历史(日期降序)
        return todayMeetings.sortedBy { it.startTime } +
               futureMeetings.sortedBy { it.startTime } +
               pastMeetings.sortedByDescending { it.startTime }
    }

    /**
     * 初始加载或刷新
     */
    fun loadMeetings() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            currentPage = 0
            hasMoreData = true
            allMeetings.clear()
            
            try {
                val meetings = repository.getMeetings(skip = 0, limit = pageSize, sort = "asc")
                // Prevention against duplicate keys (crash cause)
                allMeetings.addAll(meetings.distinctBy { it.id })
                hasMoreData = meetings.size >= pageSize
                _uiState.value = HomeUiState.Success(
                    meetings = sortMeetingsWithTodayFirst(allMeetings),
                    isLoadingMore = false,
                    hasMoreData = hasMoreData
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    /**
     * 加载更多 (下一页)
     */
    fun loadMore() {
        if (isLoadingMore || !hasMoreData) return
        
        viewModelScope.launch {
            isLoadingMore = true
            val currentState = _uiState.value
            if (currentState is HomeUiState.Success) {
                _uiState.value = currentState.copy(isLoadingMore = true)
            }
            
            try {
                val skip = allMeetings.size
                val newMeetings = repository.getMeetings(skip = skip, limit = pageSize, sort = "asc")
                
                if (newMeetings.isEmpty()) {
                    hasMoreData = false
                    // Don't create new list reference, just update flags
                    if (currentState is HomeUiState.Success) {
                        _uiState.value = currentState.copy(
                            isLoadingMore = false, 
                            hasMoreData = false
                        )
                    }
                } else {
                    // Filter out duplicates that might already exist in the list
                    val uniqueNewMeetings = newMeetings.filter { newM -> 
                        allMeetings.none { existing -> existing.id == newM.id } 
                    }
                    
                    if (uniqueNewMeetings.isNotEmpty()) {
                        allMeetings.addAll(uniqueNewMeetings)
                    }
                    // Updating hasMoreData based on raw response size to keep pagination logic correct
                    hasMoreData = newMeetings.size >= pageSize
                    
                    _uiState.value = HomeUiState.Success(
                        meetings = sortMeetingsWithTodayFirst(allMeetings),
                        isLoadingMore = false,
                        hasMoreData = hasMoreData
                    )
                }
            } catch (e: Exception) {
                // Don't override success state on load more error
                if (_uiState.value is HomeUiState.Success) {
                    _uiState.value = (_uiState.value as HomeUiState.Success).copy(isLoadingMore = false)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    suspend fun getMeetingDetails(id: Int): Meeting? {
        return try {
            repository.getMeetingById(id)
        } catch (e: Exception) {
            null
        }
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val meetings: List<Meeting>,
        val isLoadingMore: Boolean = false,
        val hasMoreData: Boolean = true
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
