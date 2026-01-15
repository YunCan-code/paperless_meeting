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

    // Vote State
    private val _currentVote = MutableStateFlow<com.example.paperlessmeeting.domain.model.Vote?>(null)
    val currentVote: StateFlow<com.example.paperlessmeeting.domain.model.Vote?> = _currentVote.asStateFlow()

    private val _voteResult = MutableStateFlow<com.example.paperlessmeeting.domain.model.VoteResult?>(null)
    val voteResult: StateFlow<com.example.paperlessmeeting.domain.model.VoteResult?> = _voteResult.asStateFlow()

    private val _hasVoted = MutableStateFlow(false)
    val hasVoted: StateFlow<Boolean> = _hasVoted.asStateFlow()

    private val _showVoteSheet = MutableStateFlow(false)
    val showVoteSheet: StateFlow<Boolean> = _showVoteSheet.asStateFlow()

    // Vote List State
    private val _voteList = MutableStateFlow<List<com.example.paperlessmeeting.domain.model.Vote>>(emptyList())
    val voteList: StateFlow<List<com.example.paperlessmeeting.domain.model.Vote>> = _voteList.asStateFlow()

    private val _showVoteListSheet = MutableStateFlow(false)
    val showVoteListSheet: StateFlow<Boolean> = _showVoteListSheet.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    fun checkAnyActiveVote() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is DashboardUiState.Success) {
                // 收集所有今日会议的投票
                val allVotes = mutableListOf<com.example.paperlessmeeting.domain.model.Vote>()
                for (meeting in currentState.activeMeetings) {
                    try {
                        val votes = repository.getVoteList(meeting.id)
                        allVotes.addAll(votes)
                    } catch (e: Exception) {
                        continue
                    }
                }
                
                if (allVotes.isEmpty()) {
                    _toastMessage.emit("当前没有进行中的投票")
                } else if (allVotes.size == 1) {
                    // 只有一个投票，直接打开
                    loadVoteDetails(allVotes.first())
                } else {
                    // 多个投票，显示列表
                    _voteList.value = allVotes
                    _showVoteListSheet.value = true
                }
            }
        }
    }

    fun submitVote(optionIds: List<Int>) {
        val voteId = _currentVote.value?.id ?: return
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId == -1) {
                    _toastMessage.emit("用户信息无效，请重新登录")
                    return@launch
                }
                repository.submitVote(voteId, userId, optionIds)
                _hasVoted.value = true
                _toastMessage.emit("投票提交成功")
            } catch (e: Exception) {
                e.printStackTrace()
                _toastMessage.emit("提交失败: ${e.message}")
            }
        }
    }

    fun dismissVoteSheet() {
        _showVoteSheet.value = false
        _currentVote.value = null
    }

    fun selectVoteFromList(vote: com.example.paperlessmeeting.domain.model.Vote) {
        _showVoteListSheet.value = false
        viewModelScope.launch {
            loadVoteDetails(vote)
        }
    }

    private suspend fun loadVoteDetails(vote: com.example.paperlessmeeting.domain.model.Vote) {
        _currentVote.value = vote
        _showVoteSheet.value = true
        _hasVoted.value = false // TODO: 如果API支持，这里应该检查用户是否已投票
        
        if (vote.status == "closed") {
            _voteResult.value = repository.getVoteResult(vote.id)
        } else {
            _voteResult.value = null
        }
    }

    fun dismissVoteListSheet() {
        _showVoteListSheet.value = false
        _voteList.value = emptyList()
    }

    init {
        loadData()
    }
    
    // ... existing ... 


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
                
                // Load Reading Progress
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
                        //自动刷新投票状态
                        checkAnyActiveVote()
                    }
                }
                
                // 监听投票结束事件
                launch {
                    socketManager.voteEndEvent.collect { data ->
                        android.util.Log.d("DashboardVM", "=== vote_end received: vote_id=${data.vote_id}, title=${data.title}")
                        _toastMessage.emit("投票已结束: ${data.title}")
                        // 如果当前正在显示这个投票，刷新结果
                        val currentVoteId = _currentVote.value?.id
                        android.util.Log.d("DashboardVM", "Current vote id: $currentVoteId, received: ${data.vote_id}")
                        if (currentVoteId == data.vote_id) {
                            android.util.Log.d("DashboardVM", "Fetching vote result...")
                            try {
                                val result = repository.getVoteResult(data.vote_id)
                                android.util.Log.d("DashboardVM", "Got result: $result")
                                _voteResult.value = result
                                _currentVote.value = _currentVote.value?.copy(status = "closed")
                                android.util.Log.d("DashboardVM", "Updated _voteResult and _currentVote status")
                            } catch (e: Exception) {
                                android.util.Log.e("DashboardVM", "Error fetching result", e)
                            }
                        } else {
                            android.util.Log.d("DashboardVM", "Vote id mismatch, not updating")
                        }
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
