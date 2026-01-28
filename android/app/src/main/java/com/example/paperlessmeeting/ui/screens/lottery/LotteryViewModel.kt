package com.example.paperlessmeeting.ui.screens.lottery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.LotteryState
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class LotteryViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<LotteryState?>(null)
    val uiState: StateFlow<LotteryState?> = _uiState.asStateFlow()
    
    // History rounds
    private val _history = MutableStateFlow<List<com.example.paperlessmeeting.domain.model.LotteryRound>>(emptyList())
    val history: StateFlow<List<com.example.paperlessmeeting.domain.model.LotteryRound>> = _history.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var socket: Socket? = null
    private var meetingId: Int = 0
    private var userId: Int = 0
    private var userName: String = ""

    fun init(meetingId: Int) {
        this.meetingId = meetingId
        this.userId = userPreferences.getUserId()
        this.userName = userPreferences.getUserName() ?: "未知用户"
        fetchHistory()
        initSocket()
    }

    private fun fetchHistory() {
        viewModelScope.launch {
            val response = repository.getLotteryHistory(meetingId)
            _history.value = response?.rounds ?: emptyList()
        }
    }

    private fun initSocket() {
        if (socket?.connected() == true) return
        
        try {
            // Replace with your actual server URL
            val options = IO.Options()
            options.path = "/socket.io"
            options.transports = arrayOf("websocket")
            
            // Assuming we can get the base URL from somewhere, hardcoding for now or need injection
            // Production URL matching API baseUrl in AppModule.kt
            val url = "https://coso.top" // Production server
            // In real app, this should come from config
            
            socket = IO.socket(url, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                // Join meeting room
                val data = JSONObject()
                data.put("meeting_id", meetingId)
                socket?.emit("join_meeting", data)
                
                // Get initial state
                socket?.emit("get_lottery_state", data)
            }

            socket?.on("lottery_state_change") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val gson = Gson()
                    val state = gson.fromJson(data.toString(), LotteryState::class.java)
                    _uiState.value = state
                    
                    // Refresh history if round finished
                    if (state.status == "RESULT") {
                        fetchHistory()
                    }
                }
            }
            
            socket?.on("lottery_error") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    _error.value = data.optString("message")
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
            _error.value = "Connection error: ${e.message}"
        }
    }
    
    fun joinLottery() {
        viewModelScope.launch {
            val data = JSONObject()
            data.put("action", "join")
            data.put("meeting_id", meetingId)
            data.put("user_id", userId.toString()) // Backend uses string key for map
            data.put("user_name", userName)
            socket?.emit("lottery_action", data)
        }
    }
    
    fun quitLottery() {
        viewModelScope.launch {
            val data = JSONObject()
            data.put("action", "quit")
            data.put("meeting_id", meetingId)
            data.put("user_id", userId.toString())
            socket?.emit("lottery_action", data)
        }
    }
    
    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        socket?.disconnect()
        socket?.off()
    }
}
