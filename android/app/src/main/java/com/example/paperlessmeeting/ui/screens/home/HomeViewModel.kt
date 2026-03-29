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
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val appSettingsState: AppSettingsState,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager
) : ViewModel() {

    val staticBaseUrl: String
        get() = appSettingsState.getStaticBaseUrl()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedMeetingId = MutableStateFlow<Int?>(null)
    val selectedMeetingId: StateFlow<Int?> = _selectedMeetingId.asStateFlow()

    private val _isCheckInSubmitting = MutableStateFlow(false)
    val isCheckInSubmitting: StateFlow<Boolean> = _isCheckInSubmitting.asStateFlow()

    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage.asSharedFlow()

    private val pageSize = 20
    private var hasMoreData = true
    private var isLoadingMore = false
    private val allMeetings = mutableListOf<Meeting>()

    init {
        loadMeetings()
        observeSocketEvents()
    }

    fun selectMeeting(id: Int?) {
        _selectedMeetingId.value = id
    }

    private fun sortMeetingsWithTodayFirst(meetings: List<Meeting>): List<Meeting> {
        val today = currentMeetingDate()

        val (todayMeetings, otherMeetings) = meetings.partition { meeting ->
            meeting.localDateOrNull() == today
        }
        val (futureMeetings, pastMeetings) = otherMeetings.partition { meeting ->
            val date = meeting.localDateOrNull()
            date != null && date.isAfter(today)
        }

        return todayMeetings.sortedBy { it.startTime } +
            futureMeetings.sortedBy { it.startTime } +
            pastMeetings.sortedByDescending { it.startTime }
    }

    fun loadMeetings() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            hasMoreData = true
            allMeetings.clear()

            try {
                val meetings = repository.getMeetings(
                    skip = 0,
                    limit = pageSize,
                    sort = "desc",
                    userId = currentUserIdOrNull()
                )
                allMeetings.addAll(meetings.distinctBy { it.id })
                hasMoreData = meetings.size >= pageSize
                publishSuccess()
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || !hasMoreData) return

        viewModelScope.launch {
            isLoadingMore = true
            val currentState = _uiState.value
            if (currentState is HomeUiState.Success) {
                _uiState.value = currentState.copy(isLoadingMore = true)
            }

            try {
                val newMeetings = repository.getMeetings(
                    skip = allMeetings.size,
                    limit = pageSize,
                    sort = "desc",
                    userId = currentUserIdOrNull()
                )

                if (newMeetings.isEmpty()) {
                    hasMoreData = false
                    if (currentState is HomeUiState.Success) {
                        _uiState.value = currentState.copy(
                            isLoadingMore = false,
                            hasMoreData = false
                        )
                    }
                } else {
                    val uniqueNewMeetings = newMeetings.filter { newMeeting ->
                        allMeetings.none { existing -> existing.id == newMeeting.id }
                    }
                    if (uniqueNewMeetings.isNotEmpty()) {
                        allMeetings.addAll(uniqueNewMeetings)
                    }
                    hasMoreData = newMeetings.size >= pageSize
                    publishSuccess()
                }
            } catch (_: Exception) {
                if (_uiState.value is HomeUiState.Success) {
                    _uiState.value = (_uiState.value as HomeUiState.Success).copy(isLoadingMore = false)
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    suspend fun getMeetingDetails(id: Int): Meeting? {
        return when (val result = repository.getMeetingById(id, currentUserIdOrNull())) {
            is Resource.Success -> result.data
            else -> null
        }
    }

    suspend fun getMeetingDetailsResult(id: Int): Resource<Meeting> {
        return repository.getMeetingById(id, currentUserIdOrNull())
    }

    fun shouldShowCheckInHint(meetingId: Int): Boolean {
        val userId = currentUserIdOrNull() ?: return false
        return !userPreferences.hasSeenCheckInHint(userId, meetingId)
    }

    fun markCheckInHintSeen(meetingId: Int) {
        val userId = currentUserIdOrNull() ?: return
        userPreferences.markCheckInHintSeen(userId, meetingId)
    }

    suspend fun checkInMeeting(id: Int): SplitDetailCheckInResult {
        val userId = currentUserIdOrNull()
            ?: return SplitDetailCheckInResult.Error("未登录，无法签到")
        if (_isCheckInSubmitting.value) {
            return SplitDetailCheckInResult.Error("正在处理签到，请稍后")
        }

        return try {
            _isCheckInSubmitting.value = true
            repository.checkIn(userId, id)
            refreshMeetingsSilently()
            when (val result = repository.getMeetingById(id, userId)) {
                is Resource.Success -> SplitDetailCheckInResult.Updated(result.data)
                is Resource.Error -> SplitDetailCheckInResult.Error(result.message)
                Resource.Loading -> SplitDetailCheckInResult.Error("签到后刷新失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            SplitDetailCheckInResult.Error("签到失败：${e.message ?: "未知错误"}")
        } finally {
            _isCheckInSubmitting.value = false
        }
    }

    suspend fun cancelCheckIn(meeting: Meeting): SplitDetailCheckInResult {
        val userId = currentUserIdOrNull()
            ?: return SplitDetailCheckInResult.Error("未登录，无法取消签到")
        val checkInId = meeting.checkInId
            ?: return SplitDetailCheckInResult.Error("当前会议没有签到记录")
        if (_isCheckInSubmitting.value) {
            return SplitDetailCheckInResult.Error("正在处理签到，请稍后")
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
                Resource.Loading -> SplitDetailCheckInResult.Error("取消签到后刷新失败")
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
            viewModelScope.launch { refreshMeetingsSilently() }
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
            val meetings = repository.getMeetings(
                skip = 0,
                limit = pageSize,
                sort = "desc",
                userId = currentUserIdOrNull()
            )
            allMeetings.clear()
            allMeetings.addAll(meetings.distinctBy { it.id })
            hasMoreData = meetings.size >= pageSize
            publishSuccess()
        } catch (e: Exception) {
            if (_uiState.value !is HomeUiState.Success) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    private fun currentUserIdOrNull(): Int? = userPreferences.getUserId().takeIf { it > 0 }

    private fun publishSuccess() {
        _uiState.value = HomeUiState.Success(
            meetings = sortMeetingsWithTodayFirst(allMeetings),
            isLoadingMore = false,
            hasMoreData = hasMoreData
        )
    }

    private fun Meeting.localDateOrNull(): LocalDate? {
        return try {
            LocalDate.parse(startTime.substringBefore(" "))
        } catch (_: Exception) {
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

sealed interface SplitDetailCheckInResult {
    data class Updated(val meeting: Meeting) : SplitDetailCheckInResult
    data class Hidden(val message: String) : SplitDetailCheckInResult
    data class Error(val message: String) : SplitDetailCheckInResult
}
