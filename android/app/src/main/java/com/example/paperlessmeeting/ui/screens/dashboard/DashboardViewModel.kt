package com.example.paperlessmeeting.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.BuildConfig
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val readingProgressManager: com.example.paperlessmeeting.data.local.ReadingProgressManager,
    private val socketManager: com.example.paperlessmeeting.data.remote.SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private var socketInitialized = false
    private var currentMeetingIds: Set<Int> = emptySet()
    private var reconnectJob: Job? = null

    init {
        setupSocketIfNeeded()
        loadData()
    }

    fun refreshData() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val userName = userPreferences.getUserName() ?: "用户"

                val todayStr = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
                val todayMeetings = repository.getMeetings(
                    limit = 100,
                    startDate = todayStr,
                    endDate = todayStr,
                    sort = "asc"
                )

                val now = java.time.LocalDateTime.now()
                val activeListWithTimes = todayMeetings.mapNotNull { meeting ->
                    try {
                        val isoTime = meeting.startTime.replace(" ", "T")
                        val time = java.time.LocalDateTime.parse(isoTime)
                        Pair(meeting, time)
                    } catch (_: Exception) {
                        null
                    }
                }

                var startIndex = 0
                for (i in 0 until activeListWithTimes.size - 1) {
                    val nextMeetingTime = activeListWithTimes[i + 1].second
                    val switchTime = nextMeetingTime.minusMinutes(15)
                    if (now.isAfter(switchTime)) {
                        startIndex = i + 1
                    } else {
                        break
                    }
                }

                val recentFiles = todayMeetings.flatMap { it.attachments.orEmpty() }.take(10)

                readingProgressManager.loadFromServer()
                val progressList = readingProgressManager.getAllProgress()

                _uiState.value = DashboardUiState.Success(
                    meetings = todayMeetings,
                    activeMeetings = todayMeetings,
                    recentFiles = recentFiles,
                    readingProgress = progressList,
                    initialPageIndex = startIndex,
                    userName = userName
                )

                currentMeetingIds = todayMeetings.map { it.id }.toSet()
                joinMeetingRooms()
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun setupSocketIfNeeded() {
        if (socketInitialized) return
        socketInitialized = true
        setupSocket()
    }

    private fun joinMeetingRooms() {
        currentMeetingIds.forEach { meetingId ->
            socketManager.joinMeeting(meetingId)
        }
    }

    private fun setupSocket() {
        viewModelScope.launch {
            try {
                socketManager.connect(BuildConfig.SOCKET_BASE_URL)

                launch {
                    socketManager.connectionState.collect { connected ->
                        if (connected) {
                            reconnectJob?.cancel()
                            reconnectJob = null
                            joinMeetingRooms()
                        } else {
                            scheduleReconnect()
                        }
                    }
                }

                launch {
                    socketManager.voteStartEvent.collect { vote ->
                        _toastMessage.emit("收到新投票: ${vote.title}")
                    }
                }

                launch {
                    socketManager.voteEndEvent.collect { data ->
                        _toastMessage.emit("投票已结束: ${data.title}")
                    }
                }

                launch {
                    socketManager.meetingChangedEvent.collect {
                        loadData()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            delay(1500)
            socketManager.connect(BuildConfig.SOCKET_BASE_URL)
        }
    }
}
