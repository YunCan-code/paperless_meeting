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

    var isMeetingFinished by mutableStateOf(false)
        private set

    // 3. 中奖名单
    private val _winners = MutableStateFlow<List<String>>(emptyList())
    val winners: StateFlow<List<String>> = _winners.asStateFlow()

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

    // --- Socket Logic ---
    
    fun connectToMeeting(meetingId: Int) {
        if (currentMeetingId == meetingId && socketConnected) return
        
        if (currentUserId.isEmpty()) loadUserInfo()
        
        disconnect()
        
        currentMeetingId = meetingId
        myStatus = ParticipationStatus.NotJoined

        try {
            val opts = IO.Options()
            opts.path = "/socket.io"
            opts.transports = arrayOf("websocket")
            opts.reconnection = true
            
            socket = IO.socket("https://coso.top", opts)

            socket?.on(Socket.EVENT_CONNECT) {
                socketConnected = true
                
                // 1. Join Room
                val joinData = JSONObject()
                joinData.put("meeting_id", meetingId)
                socket?.emit("join_meeting", joinData)

                // 2. Sync State (Critical Fix)
                val syncData = JSONObject()
                syncData.put("meeting_id", meetingId)
                syncData.put("user_id", currentUserId)
                socket?.emit("get_lottery_state", syncData)
                
                // 3. Get History (for winning list)
                 val historyPayload = JSONObject()
                 historyPayload.put("action", "get_history")
                 historyPayload.put("meeting_id", meetingId)
                 socket?.emit("lottery_action", historyPayload)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                socketConnected = false
            }

            // --- State Handling ---
            
            // Sync Initial State / Reconnect
            socket?.on("lottery_state_sync") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    handleStateUpdate(data)
                }
            }

            // Handle State Change Broadcast
            socket?.on("lottery_state_change") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    handleStateUpdate(data)
                }
            }
            
            // Handle Players Update (Append/Remove for visual count mostly)
            socket?.on("lottery_players_update") { args ->
                 if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    // Update count
                    participantsCount = data.optInt("count", participantsCount)
                    
                    // Update my status if specifically mentioned
                    val removedId = data.optString("removed_user_id", "")
                    if (removedId == currentUserId) {
                        myStatus = ParticipationStatus.Removed
                    }
                    
                    // Check if I am in all_participants list (if sent)
                    val allP = data.optJSONArray("all_participants")
                    if (allP != null) {
                         var found = false
                         for(i in 0 until allP.length()) {
                             val p = allP.getJSONObject(i)
                             if (p.optString("id") == currentUserId) {
                                 found = true; break
                             }
                         }
                         if (found) myStatus = ParticipationStatus.Joined
                         else if (myStatus == ParticipationStatus.Joined) myStatus = ParticipationStatus.NotJoined
                    }
                 }
            }
            
            // Handle History (for winners list)
            socket?.on("lottery_history") { args ->
                 // ... existing history logic mostly fine for winners list ...
                 if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val rounds = data.optJSONArray("rounds")
                    val newWinners = mutableListOf<String>()
                    if (rounds != null) {
                        for (i in 0 until rounds.length()) {
                            val r = rounds.getJSONObject(i)
                            val wList = r.optJSONArray("winners")
                            if (wList != null) {
                                for (j in 0 until wList.length()) {
                                    val w = wList.getJSONObject(j)
                                    newWinners.add(w.optString("name"))
                                }
                            }
                        }
                    }
                    _winners.value = newWinners
                 }
            }

            socket?.connect()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleStateUpdate(data: JSONObject) {
        val status = data.optString("status")
        val config = data.optJSONObject("config")
        val participantsCountVal = data.optInt("participants_count", 0)
        
        // Update basic info
        participantsCount = participantsCountVal
        
        var title = "暂无抽签"
        if (config != null) {
            title = config.optString("title", "抽签")
        }
        
        when (status) {
            "IDLE" -> currentRoundTitle = "等待抽签开始..."
            "PREPARING" -> currentRoundTitle = "$title (准备中)"
            "ROLLING" -> currentRoundTitle = "$title (抽签进行中...)"
            "RESULT" -> currentRoundTitle = "$title (已结束)"
        }
        
        // Update My Status
        // is_joined is sent in sync, but not always in change (unless broadcast to specific user? No, broadcast is to room)
        // Wait, socket_manager sends 'lottery_state_sync' to SID, so it HAS is_joined.
        // 'lottery_state_change' is broadcast, so it DOES NOT have is_joined specific to user usually.
        // So for change, we rely on previous status or 'lottery_players_update'.
        
        if (data.has("is_joined")) {
            val isJoined = data.getBoolean("is_joined")
            myStatus = if(isJoined) ParticipationStatus.Joined else ParticipationStatus.NotJoined
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
        // Optimistic update for responsiveness, server will confirm/reject
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
