package com.example.paperlessmeeting.data.remote

import android.util.Log
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteOptionResult
import com.google.gson.Gson
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Socket.IO 客户端管理器
 * 用于实时投票等 WebSocket 通信
 */
@Singleton
class SocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {

    private var socket: Socket? = null
    private val gson = Gson()

    // 事件流
    private val _voteStartEvent = MutableSharedFlow<Vote>(extraBufferCapacity = 1)
    val voteStartEvent: SharedFlow<Vote> = _voteStartEvent.asSharedFlow()

    private val _voteUpdateEvent = MutableSharedFlow<VoteUpdateData>(extraBufferCapacity = 1)
    val voteUpdateEvent: SharedFlow<VoteUpdateData> = _voteUpdateEvent.asSharedFlow()

    private val _voteEndEvent = MutableSharedFlow<VoteEndData>(extraBufferCapacity = 1)
    val voteEndEvent: SharedFlow<VoteEndData> = _voteEndEvent.asSharedFlow()

    private val _connectionState = MutableSharedFlow<Boolean>(replay = 1)
    val connectionState: SharedFlow<Boolean> = _connectionState.asSharedFlow()

    fun connect(serverUrl: String) {
        if (socket?.connected() == true) return

        try {
            val options = IO.Options().apply {
                // transports = arrayOf("websocket") // 允许自动升级，提高兼容性
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 2000
                
                // 使用配置好SSL信任的 Shared OkHttpClient
                callFactory = okHttpClient
                webSocketFactory = okHttpClient
            }

            socket = IO.socket(serverUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected")
                _connectionState.tryEmit(true)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
                _connectionState.tryEmit(false)
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Connection error: ${args.firstOrNull()}")
            }

            // 投票事件
            socket?.on("vote_start") { args ->
                try {
                    val json = args[0] as JSONObject
                    val vote = gson.fromJson(json.toString(), Vote::class.java)
                    _voteStartEvent.tryEmit(vote)
                    Log.d(TAG, "Received vote_start: ${vote.title}")
                    
                    // 全局 Toast 通知
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "收到新投票: ${vote.title}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing vote_start", e)
                }
            }

            socket?.on("vote_update") { args ->
                try {
                    val json = args[0] as JSONObject
                    val data = gson.fromJson(json.toString(), VoteUpdateData::class.java)
                    _voteUpdateEvent.tryEmit(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing vote_update", e)
                }
            }

            socket?.on("vote_end") { args ->
                try {
                    val json = args[0] as JSONObject
                    val data = gson.fromJson(json.toString(), VoteEndData::class.java)
                    _voteEndEvent.tryEmit(data)
                    Log.d(TAG, "Received vote_end: ${data.vote_id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing vote_end", e)
                }
            }

            socket?.connect()

        } catch (e: Exception) {
            Log.e(TAG, "Socket connection error", e)
        }
    }

    fun joinMeeting(meetingId: Int) {
        socket?.emit("join_meeting", JSONObject().apply {
            put("meeting_id", meetingId)
        })
        Log.d(TAG, "Joining meeting room: $meetingId")
    }

    fun leaveMeeting(meetingId: Int) {
        socket?.emit("leave_meeting", JSONObject().apply {
            put("meeting_id", meetingId)
        })
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    companion object {
        private const val TAG = "SocketManager"
    }
}

// 数据类
data class VoteUpdateData(
    val vote_id: Int,
    val results: List<VoteOptionResult>
)

data class VoteEndData(
    val vote_id: Int,
    val title: String,
    val total_voters: Int,
    val results: List<VoteOptionResult>
)
