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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

                // 1. Get Today's Meetings (for Hero Card)
                val todayStr = java.time.LocalDate.now().toString()
                val todayMeetings = repository.getMeetings(
                    limit = 100, // Ensure we get all of today's meetings
                    startDate = todayStr,
                    endDate = todayStr,
                    sort = "asc"
                )
                
                // Parse times for internal logic (focus index)
                val now = java.time.LocalDateTime.now()
                val activeListWithTimes = todayMeetings.mapNotNull { meeting ->
                    try {
                        val isoTime = meeting.startTime.replace(" ", "T")
                        val time = java.time.LocalDateTime.parse(isoTime)
                        Pair(meeting, time)
                    } catch (e: Exception) {
                        null
                    }
                } // Already sorted from backend

                // 2. Determine "Focus" Index based on 15-min Logic
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

                // 3. Get Recent Files (Mock or separate API call if needed, here just use today's)
                val recentFiles = todayMeetings.flatMap { it.attachments.orEmpty() }.take(10)
                
                // Load Reading Progress from server first, then local
                readingProgressManager.loadFromServer()
                val progressList = readingProgressManager.getAllProgress()

                _uiState.value = DashboardUiState.Success(
                    meetings = todayMeetings,  // Only show today's meetings in raw list if needed, or separate
                    activeMeetings = todayMeetings, 
                    recentFiles = recentFiles,
                    readingProgress = progressList,
                    initialPageIndex = startIndex,
                    userName = userName
                )
                
                // 初始化 Socket 连接并监听投票事件
                setupSocket(todayMeetings)
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun setupSocket(meetings: List<Meeting>) {
        viewModelScope.launch {
            try {
                // 连接 WebSocket 服务器
                socketManager.connect("https://coso.top") // 使用配置的服务器地址
                
                // 等待连接成功后再加入房间
                launch {
                    socketManager.connectionState.collect { connected ->
                        if (connected) {
                            meetings.forEach { meeting ->
                                socketManager.joinMeeting(meeting.id)
                            }
                        }
                    }
                }
                
                // 监听投票开始事件
                launch {
                    socketManager.voteStartEvent.collect { vote ->
                        _toastMessage.emit("收到新投票: ${vote.title}")
                    }
                }
                
                // 监听投票结束事件
                launch {
                    socketManager.voteEndEvent.collect { data ->
                        _toastMessage.emit("投票已结束: ${data.title}")
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
