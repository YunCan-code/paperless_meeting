package com.example.paperlessmeeting.ui.screens.lottery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

sealed class ParticipationStatus {
    object NotJoined : ParticipationStatus()
    object Joined : ParticipationStatus()
    object Removed : ParticipationStatus() // 被移除了
}

@HiltViewModel
class LotteryViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    // 1. 列表页状态
    private val _meetings = MutableStateFlow<List<Meeting>>(emptyList())
    val meetings: StateFlow<List<Meeting>> = _meetings.asStateFlow()
    
    // 2. 详情页/Socket状态
    var socketConnected by mutableStateOf(false)
        private set
        
    var currentRoundTitle by mutableStateOf("等待抽签开始...")
        private set
        
    var myStatus by mutableStateOf<ParticipationStatus>(ParticipationStatus.NotJoined)
        private set
        
    var participantsCount by mutableStateOf(0)
        private set

    private var socket: Socket? = null
    private var currentMeetingId: Int? = null
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var currentUserDept: String = ""

    init {
        fetchActiveMeetings()
        loadUserInfo()
    }
    
    private fun loadUserInfo() {
        val id = userPreferences.getUserId()
        if (id != -1) {
            currentUserId = id.toString()
            currentUserName = userPreferences.getUserName() ?: "User"
            currentUserDept = userPreferences.getUserDept() ?: ""
        }
    }

    private fun fetchActiveMeetings() {
        viewModelScope.launch {
            try {
                // Fetch meetings with ACTIVE/PENDING lottery (from Backend API)
                val activeMeetings = repository.getActiveLotteryMeetings()
                _meetings.value = activeMeetings
            } catch (e: Exception) {
                e.printStackTrace()
                _meetings.value = emptyList()
            }
        }
    }

    // --- Socket Logic ---

    fun connectToMeeting(meetingId: Int) {
        if (currentMeetingId == meetingId && socketConnected) return
        
        // If user info missing, try reloading
        if (currentUserId.isEmpty()) loadUserInfo()
        
        disconnect() // Disconnect previous
        
        currentMeetingId = meetingId
        myStatus = ParticipationStatus.NotJoined // Reset status

        try {
            val opts = IO.Options()
            opts.path = "/socket.io"
            opts.transports = arrayOf("websocket")
            // Reconnection config
            opts.reconnection = true
            opts.reconnectionAttempts = 10
            
            socket = IO.socket("https://coso.top", opts)

            socket?.on(Socket.EVENT_CONNECT) {
                socketConnected = true
                // Join room
                socket?.emit("join_meeting", JSONObject().put("meeting_id", meetingId))
                
                // Check if already in participants list? 
                // Currently backend doesn't push full list on join_meeting immediate response unless customized.
                // Rely on updates.
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                socketConnected = false
            }

            // Listen for prepare/start info
            socket?.on("lottery_prepare") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    currentRoundTitle = data.optString("title", "抽签准备中")
                }
            }
            
            // Listen for start (rolling)
            socket?.on("lottery_start") { 
                currentRoundTitle = "抽签进行中..."
            }
            
            // Listen for stop (result)
            socket?.on("lottery_stop") {
                 currentRoundTitle = "抽签结束，等待下一轮"
            }

            // Listen for participants update
            socket?.on("lottery_players_update") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    participantsCount = data.optInt("count", 0)
                    
                    val removedId = data.optString("removed_user_id", "")
                    if (removedId == currentUserId) {
                        myStatus = ParticipationStatus.Removed
                    }
                    
                    // Logic to confirm joined status
                    val allParticipants = data.optJSONArray("all_participants")
                    if (allParticipants != null) {
                        var found = false
                        for (i in 0 until allParticipants.length()) {
                            val p = allParticipants.getJSONObject(i)
                            if (p.optString("id") == currentUserId) {
                                found = true
                                break
                            }
                        }
                        
                        if (found) {
                             if (myStatus != ParticipationStatus.Joined) {
                                 myStatus = ParticipationStatus.Joined
                             }
                        } else {
                             // If previously joined but now not found => Removed
                             if (myStatus == ParticipationStatus.Joined) {
                                 myStatus = ParticipationStatus.Removed
                             }
                        }
                    }
                }
            }
            
            socket?.connect()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun joinLottery() {
        if (socket == null || !socketConnected) return
        
        val userObj = JSONObject()
        userObj.put("id", currentUserId)
        userObj.put("name", currentUserName)
        userObj.put("department", currentUserDept)

        val data = JSONObject()
        data.put("action", "join")
        data.put("meeting_id", currentMeetingId)
        data.put("user", userObj)

        socket?.emit("lottery_action", data)
        // Optimistic update
        // myStatus = ParticipationStatus.Joined 
        // Better wait for verification from server callback/update, but to be responsive:
        myStatus = ParticipationStatus.Joined
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        socketConnected = false
        currentRoundTitle = "等待抽签..."
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
