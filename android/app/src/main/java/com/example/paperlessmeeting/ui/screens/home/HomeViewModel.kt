package com.example.paperlessmeeting.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.utils.Resource
import com.example.paperlessmeeting.utils.currentMeetingDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val appSettingsState: AppSettingsState,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager
) : ViewModel() {

    val staticBaseUrl: String get() = appSettingsState.getStaticBaseUrl()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedMeetingId = MutableStateFlow<Int?>(null)
    val selectedMeetingId: StateFlow<Int?> = _selectedMeetingId.asStateFlow()
    private val _isCheckInSubmitting = MutableStateFlow(false)
    val isCheckInSubmitting: StateFlow<Boolean> = _isCheckInSubmitting.asStateFlow()
    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage.asSharedFlow()

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
        observeSocketEvents()
    }

    /**
     * 会议排序：今天 -> 未来 -> 历史
     */
    private fun sortMeetingsWithTodayFirst(meetings: List<Meeting>): List<Meeting> {
        val today = currentMeetingDate()
        
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
                val userId = userPreferences.getUserId().takeIf { it > 0 }
                // Use sort=desc to get most recent meetings first (today/future will be at the top)
                val meetings = repository.getMeetings(skip = 0, limit = pageSize, sort = "desc", userId = userId)
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
                val userId = userPreferences.getUserId().takeIf { it > 0 }
                val newMeetings = repository.getMeetings(skip = skip, limit = pageSize, sort = "desc", userId = userId)
                
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
        val userId = userPreferences.getUserId().takeIf { it > 0 }
        return when (val result = repository.getMeetingById(id, userId)) {
            is Resource.Success -> result.data
            else -> null
        }
    }

    suspend fun getMeetingDetailsResult(id: Int): Resource<Meeting> {
        val userId = userPreferences.getUserId().takeIf { it > 0 }
        return repository.getMeetingById(id, userId)
    }

    suspend fun checkInMeeting(id: Int): SplitDetailCheckInResult {
        val userId = userPreferences.getUserId().takeIf { it > 0 }
            ?: return SplitDetailCheckInResult.Error("未登录，无法签到")
        if (_isCheckInSubmitting.value) {
            return SplitDetailCheckInResult.Error("正在处理签到，请稍候")
        }

        return try {
            _isCheckInSubmitting.value = true
            repository.checkIn(userId, id)
            refreshMeetingsSilently()
            when (val result = repository.getMeetingById(id, userId)) {
                is Resource.Success -> SplitDetailCheckInResult.Updated(result.data)
                is Resource.Error -> SplitDetailCheckInResult.Error(result.message ?: "签到后刷新失败")
                else -> SplitDetailCheckInResult.Error("签到后刷新失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SplitDetailCheckInResult.Error("签到失败：${e.message ?: "未知错误"}")
        } finally {
            _isCheckInSubmitting.value = false
        }
    }

    suspend fun cancelCheckIn(meeting: Meeting): SplitDetailCheckInResult {
        val userId = userPreferences.getUserId().takeIf { it > 0 }
            ?: return SplitDetailCheckInResult.Error("未登录，无法取消签到")
        val checkInId = meeting.checkInId
            ?: return SplitDetailCheckInResult.Error("当前会议没有签到记录")
        if (_isCheckInSubmitting.value) {
            return SplitDetailCheckInResult.Error("正在处理签到，请稍候")
        }

        return try {
            _isCheckInSubmitting.value = true
            repository.cancelCheckIn(checkInId, userId)
            refreshMeetingsSilently()
            when (val result = repository.getMeetingById(meeting.id, userId)) {
                is Resource.Success -> SplitDetailCheckInResult.Updated(result.data)
                is Resource.Error -> {
                    if (result.message == "HTTP_404") {
                        SplitDetailCheckInResult.Hidden("取消签到后，该会议已不可见")
                    } else {
                        SplitDetailCheckInResult.Error("取消签到后刷新失败：${result.message}")
                    }
                }
                else -> SplitDetailCheckInResult.Error("取消签到后刷新失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SplitDetailCheckInResult.Error("取消签到失败：${e.message ?: "未知错误"}")
        } finally {
            _isCheckInSubmitting.value = false
        }
    }

    fun refreshOnVisible() {
        if (_uiState.value is HomeUiState.Success) {
            viewModelScope.launch {
                refreshMeetingsSilently()
            }
        } else {
            loadMeetings()
        }
    }

    private fun observeSocketEvents() {
        socketManager.connect(appSettingsState.getSocketBaseUrl())
        viewModelScope.launch {
            socketManager.meetingChangedEvent.collectLatest {
                refreshMeetingsSilently()
            }
        }
    }

    private suspend fun refreshMeetingsSilently() {
        try {
            val userId = userPreferences.getUserId().takeIf { it > 0 }
            val meetings = repository.getMeetings(skip = 0, limit = pageSize, sort = "desc", userId = userId)
            allMeetings.clear()
            allMeetings.addAll(meetings.distinctBy { it.id })
            hasMoreData = meetings.size >= pageSize
            _uiState.value = HomeUiState.Success(
                meetings = sortMeetingsWithTodayFirst(allMeetings),
                isLoadingMore = false,
                hasMoreData = hasMoreData
            )
        } catch (e: Exception) {
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    private suspend fun emitActionMessage(message: String) {
        _actionMessage.emit(message)
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

sealed interface SplitDetailCheckInResult {
    data class Updated(val meeting: Meeting) : SplitDetailCheckInResult
    data class Hidden(val message: String) : SplitDetailCheckInResult
    data class Error(val message: String) : SplitDetailCheckInResult
}
