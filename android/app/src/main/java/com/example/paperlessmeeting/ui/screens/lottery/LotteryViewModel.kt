package com.example.paperlessmeeting.ui.screens.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryRound
import com.example.paperlessmeeting.domain.model.LotterySession
import com.example.paperlessmeeting.domain.model.LotteryWinner
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WinnerAnnouncementData(
    val title: String,
    val message: String
)

@HiltViewModel
class LotteryViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager,
    private val appSettingsState: AppSettingsState
) : ViewModel() {

    private val _uiState = MutableStateFlow<LotterySession?>(null)
    val uiState: StateFlow<LotterySession?> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<LotteryRound>>(emptyList())
    val history: StateFlow<List<LotteryRound>> = _history.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _winnerAnnouncement = MutableSharedFlow<WinnerAnnouncementData>(extraBufferCapacity = 1)
    val winnerAnnouncement: SharedFlow<WinnerAnnouncementData> = _winnerAnnouncement.asSharedFlow()

    private var meetingId: Int = 0
    private var userId: Int = 0
    private var listenersStarted = false

    fun init(meetingId: Int) {
        if (meetingId <= 0) return

        val previousMeetingId = this.meetingId
        this.meetingId = meetingId
        this.userId = userPreferences.getUserId()

        if (!listenersStarted) {
            listenersStarted = true
            initSocketListener()
        }

        socketManager.connect(appSettingsState.getSocketBaseUrl())
        if (previousMeetingId != 0 && previousMeetingId != meetingId) {
            socketManager.leaveMeeting(previousMeetingId)
        }
        socketManager.joinMeeting(meetingId)
        refreshSession()
    }

    fun joinLottery() {
        if (meetingId == 0 || userId == 0) return
        viewModelScope.launch {
            val session = repository.joinLotteryPool(meetingId, userId)
            if (session == null) {
                _error.value = "加入抽签失败"
            } else {
                handleSessionUpdate(session)
            }
        }
    }

    fun quitLottery() {
        if (meetingId == 0 || userId == 0) return
        viewModelScope.launch {
            val session = repository.quitLotteryPool(meetingId, userId)
            if (session == null) {
                _error.value = "退出抽签失败"
            } else {
                handleSessionUpdate(session)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun refreshSession() {
        if (meetingId == 0) return
        viewModelScope.launch {
            val session = repository.getLotterySession(meetingId, userId)
            if (session == null) {
                _error.value = "加载抽签状态失败"
            } else {
                handleSessionUpdate(session)
            }
        }
    }

    private fun initSocketListener() {
        viewModelScope.launch {
            socketManager.lotterySessionEvent.collect { session ->
                if (session.meeting_id == meetingId) {
                    handleSessionUpdate(
                        session.mergePublicSessionUpdate(
                            currentUserId = userId.takeIf { it > 0 },
                            previousSession = _uiState.value
                        )
                    )
                }
            }
        }

        viewModelScope.launch {
            socketManager.lotteryErrorEvent.collect { errorMsg ->
                _error.value = errorMsg
            }
        }
    }

    private fun handleSessionUpdate(session: LotterySession) {
        val previousState = _uiState.value
        if (shouldAnnounceWinner(previousState, session)) {
            _winnerAnnouncement.tryEmit(
                WinnerAnnouncementData(
                    title = session.current_round?.let { "${it.roundOrderLabel()}中签提醒" } ?: "中签提醒",
                    message = "你已进入本轮中签名单。"
                )
            )
        }

        _uiState.value = session
        _history.value = session.finishedRounds()
    }

    private fun shouldAnnounceWinner(
        previousState: LotterySession?,
        newState: LotterySession
    ): Boolean {
        if (previousState?.session_status != "rolling") return false
        if (newState.session_status !in setOf("result", "completed")) return false
        return newState.winners.any { winnerMatchesUser(it, userId) }
    }

    private fun winnerMatchesUser(winner: LotteryWinner, currentUserId: Int): Boolean {
        return winner.user_id == currentUserId || winner.id == currentUserId
    }

    override fun onCleared() {
        super.onCleared()
        if (meetingId != 0) {
            socketManager.leaveMeeting(meetingId)
        }
    }
}
