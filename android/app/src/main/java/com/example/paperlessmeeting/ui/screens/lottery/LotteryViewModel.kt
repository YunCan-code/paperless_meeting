package com.example.paperlessmeeting.ui.screens.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryState
import com.example.paperlessmeeting.data.remote.SocketManager
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class LotteryViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LotteryState?>(null)
    val uiState: StateFlow<LotteryState?> = _uiState.asStateFlow()
    
    // History rounds
    private val _history = MutableStateFlow<List<com.example.paperlessmeeting.domain.model.LotteryRound>>(emptyList())
    val history: StateFlow<List<com.example.paperlessmeeting.domain.model.LotteryRound>> = _history.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var meetingId: Int = 0
    private var userId: Int = 0
    private var userName: String = ""

    fun init(meetingId: Int) {
        this.meetingId = meetingId
        this.userId = userPreferences.getUserId()
        this.userName = userPreferences.getUserName() ?: "未知用户"
        fetchHistory()
        initSocketListener()
        
        // Ensure connection
        socketManager.connect("https://coso.top")
        socketManager.joinMeeting(meetingId)
        socketManager.getLotteryState(meetingId, userId)
    }

    fun getCurrentUserId(): Int = userId

    private fun fetchHistory() {
        viewModelScope.launch {
            val response = repository.getLotteryHistory(meetingId)
            _history.value = response?.rounds ?: emptyList()
        }
    }

    private fun initSocketListener() {
        viewModelScope.launch {
            socketManager.lotteryStateEvent.collect { state ->
                println("[Lottery] State change received: ${state.status}")
                _uiState.value = state

                // Refresh history if round finished
                if (state.status == "RESULT") {
                    fetchHistory()
                }
            }
        }

        viewModelScope.launch {
            socketManager.lotteryPlayersEvent.collect { data ->
                 try {
                        println("[Lottery] Players update received: $data")

                        // 解析参与者列表
                        val participantsArray = data.optJSONArray("all_participants") ?: data.optJSONArray("participants")
                        val participantsList = mutableListOf<com.example.paperlessmeeting.domain.model.LotteryParticipant>()

                        if (participantsArray != null) {
                            for (i in 0 until participantsArray.length()) {
                                val p = participantsArray.getJSONObject(i)
                                participantsList.add(
                                    com.example.paperlessmeeting.domain.model.LotteryParticipant(
                                        id = p.opt("id") ?: 0,
                                        name = p.optString("name", ""),
                                        sid = p.optString("sid", null),
                                        avatar = p.optString("avatar", null),
                                        department = p.optString("department", null)
                                    )
                                )
                            }
                        }

                        // 更新参与者数量和列表到当前状态
                        val currentState = _uiState.value
                        if (currentState != null) {
                            val updatedState = currentState.copy(
                                participant_count = data.optInt("participant_count", data.optInt("count", currentState.participant_count)),
                                participants = participantsList
                            )
                            _uiState.value = updatedState
                        } else {
                            // 如果当前状态为空，创建一个新的状态
                            _uiState.value = LotteryState(
                                status = "PREPARING",
                                participants = participantsList,
                                participant_count = data.optInt("participant_count", data.optInt("count", 0))
                            )
                        }
                    } catch (e: Exception) {
                        println("[Lottery] Error parsing players update: ${e.message}")
                        e.printStackTrace()
                    }
            }
        }

        viewModelScope.launch {
            socketManager.lotteryErrorEvent.collect { errorMsg ->
                 println("[Lottery] Error received: $errorMsg")
                 _error.value = errorMsg
            }
        }
    }
    
    fun joinLottery() {
        viewModelScope.launch {
            println("[Lottery] Attempting to join - userId: $userId, userName: $userName, meetingId: $meetingId")
            val data = JSONObject()
            data.put("action", "join")
            data.put("meeting_id", meetingId)
            data.put("user_id", userId) 
            data.put("user_name", userName)
            data.put("department", "") 
            data.put("avatar", "") 
            println("[Lottery] Emitting lottery_action with data: $data")
            socketManager.emitLotteryAction(data)
        }
    }
    
    fun quitLottery() {
        viewModelScope.launch {
            val data = JSONObject()
            data.put("action", "quit")
            data.put("meeting_id", meetingId)
            data.put("user_id", userId) 
            socketManager.emitLotteryAction(data)
        }
    }
    
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT disconnect socketManager as it is global
        // But maybe leave room?
        // socketManager.leaveMeeting(meetingId) 
        // We probably shouldn't leave immediately if we want to receive notifications?
        // But for Lottery context, maybe leaving is fine.
        // Dashboard uses joinMeeting too.
        // If we leave, dashboard might stop receiving updates?
        // SocketManager doesn't reference count rooms.
        // So if we leave, we leave.
        // It's safer NOT to leave if other screens might be interested, or trust user to just close app.
        // Or if user goes back to list, maybe we should leave?
        // For now, removing socket disconnect logic.
    }
}
