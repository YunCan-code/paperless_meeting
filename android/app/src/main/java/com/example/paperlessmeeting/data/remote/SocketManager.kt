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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.paperlessmeeting.MainActivity
import com.example.paperlessmeeting.R

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

    init {
        createNotificationChannel()
    }

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
        // 如果 socket 已经初始化过，不要重复创建
        if (socket != null) {
            // 如果已经连接或正在连接，直接返回
            if (socket?.connected() == true) return
            // 如果 socket 存在但未连接，尝试重连
            socket?.connect()
            return
        }

        try {
            val options = IO.Options().apply {
                transports = arrayOf("websocket") // 强制使用WebSocket，跳过Polling，避免多Worker模式下的Sticky Session问题
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
            
            setupLotteryListeners()

            // 投票事件
            socket?.on("vote_start") { args ->
                try {
                    val json = args[0] as JSONObject
                    val vote = gson.fromJson(json.toString(), Vote::class.java)
                    _voteStartEvent.tryEmit(vote)
                    Log.d(TAG, "Received vote_start: ${vote.title}")
                    
                    // 全局 Toast 通知
                    Handler(Looper.getMainLooper()).post {
                        // Toast.makeText(context, "收到新投票: ${vote.title}", Toast.LENGTH_LONG).show()
                        sendVoteNotification(vote)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "投票通知"
            val descriptionText = "当有新的投票开始时通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendVoteNotification(vote: Vote) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("新投票开启")
                .setContentText(vote.title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                notify(vote.id, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission missing", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
        }
    }

    // Lottery Events
    private val _lotteryStateEvent = MutableSharedFlow<com.example.paperlessmeeting.domain.model.LotteryState>(extraBufferCapacity = 1)
    val lotteryStateEvent: SharedFlow<com.example.paperlessmeeting.domain.model.LotteryState> = _lotteryStateEvent.asSharedFlow()

    private val _lotteryPlayersEvent = MutableSharedFlow<JSONObject>(extraBufferCapacity = 1)
    val lotteryPlayersEvent: SharedFlow<JSONObject> = _lotteryPlayersEvent.asSharedFlow()

    private val _lotteryErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val lotteryErrorEvent: SharedFlow<String> = _lotteryErrorEvent.asSharedFlow()

    fun emitLotteryAction(data: JSONObject) {
        socket?.emit("lottery_action", data)
    }

    fun getLotteryState(meetingId: Int, userId: Int) {
        val data = JSONObject()
        data.put("meeting_id", meetingId)
        data.put("user_id", userId)
        socket?.emit("get_lottery_state", data)
    }

    private fun setupLotteryListeners() {
         socket?.on("lottery_state_change") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "State change received: $data")
                    val state = gson.fromJson(data.toString(), com.example.paperlessmeeting.domain.model.LotteryState::class.java)
                    _lotteryStateEvent.tryEmit(state)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing lottery_state_change", e)
            }
        }

        socket?.on("lottery_state_sync") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    Log.d(TAG, "State sync received: $data")
                    val state = gson.fromJson(data.toString(), com.example.paperlessmeeting.domain.model.LotteryState::class.java)
                    _lotteryStateEvent.tryEmit(state)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing lottery_state_sync", e)
            }
        }

        socket?.on("lottery_players_update") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    // Log.d(TAG, "Players update received: $data")
                    _lotteryPlayersEvent.tryEmit(data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing lottery_players_update", e)
            }
        }

        socket?.on("lottery_error") { args ->
            try {
                if (args.isNotEmpty()) {
                    val data = args[0] as JSONObject
                    val errorMsg = data.optString("message")
                    Log.d(TAG, "Error received: $errorMsg")
                    _lotteryErrorEvent.tryEmit(errorMsg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing lottery_error", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "SocketManager"
        private const val CHANNEL_ID = "vote_channel"
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
