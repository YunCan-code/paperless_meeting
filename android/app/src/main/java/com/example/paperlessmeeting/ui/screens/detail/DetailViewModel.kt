package com.example.paperlessmeeting.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteResult
import com.example.paperlessmeeting.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val socketManager: SocketManager,
    private val userPreferences: UserPreferences,
    private val appSettingsState: AppSettingsState,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val GLOBAL_VISIBILITY_SETTING_KEY = "meeting_visibility_hide_after_hours"
        private const val DEFAULT_HIDDEN_MESSAGE = "当前会议暂不可见"
    }

    private val meetingId: String? = savedStateHandle["meetingId"]

    val staticBaseUrl: String
        get() = appSettingsState.getStaticBaseUrl()

    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage.asSharedFlow()

    private val _exitDetail = MutableSharedFlow<String>()
    val exitDetail: SharedFlow<String> = _exitDetail.asSharedFlow()

    private val _isCheckInSubmitting = MutableStateFlow(false)
    val isCheckInSubmitting: StateFlow<Boolean> = _isCheckInSubmitting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _currentVote = MutableStateFlow<Vote?>(null)
    val currentVote: StateFlow<Vote?> = _currentVote.asStateFlow()

    private val _voteResult = MutableStateFlow<VoteResult?>(null)
    val voteResult: StateFlow<VoteResult?> = _voteResult.asStateFlow()

    private val _hasVoted = MutableStateFlow(false)
    val hasVoted: StateFlow<Boolean> = _hasVoted.asStateFlow()

    private val _showVoteSheet = MutableStateFlow(false)
    val showVoteSheet: StateFlow<Boolean> = _showVoteSheet.asStateFlow()

    init {
        loadMeeting(showLoading = true)
        observeSocketEvents()
        checkActiveVote()
    }

    private fun currentUserIdOrNull(): Int? = userPreferences.getUserId().takeIf { it > 0 }

    private fun loadMeeting(
        showLoading: Boolean = false,
        hiddenMessage: String = DEFAULT_HIDDEN_MESSAGE
    ) {
        viewModelScope.launch {
            fetchMeeting(
                showLoading = showLoading,
                hiddenMessage = hiddenMessage
            )
        }
    }

    private suspend fun refreshMeetingSilently(
        hiddenMessage: String = DEFAULT_HIDDEN_MESSAGE
    ): Boolean {
        return fetchMeeting(
            showLoading = false,
            hiddenMessage = hiddenMessage
        )
    }

    private suspend fun fetchMeeting(
        showLoading: Boolean,
        hiddenMessage: String
    ): Boolean {
        val id = meetingId?.toIntOrNull()
        val hasSuccessContent = _uiState.value is DetailUiState.Success

        if (id == null) {
            if (showLoading || !hasSuccessContent) {
                _uiState.value = DetailUiState.Error("Invalid Meeting ID")
            }
            return false
        }

        if (showLoading && !hasSuccessContent) {
            _uiState.value = DetailUiState.Loading
        } else if (hasSuccessContent) {
            _isRefreshing.value = true
        }

        return try {
            when (val result = repository.getMeetingById(id, currentUserIdOrNull())) {
                is Resource.Success -> {
                    _uiState.value = DetailUiState.Success(result.data)
                    socketManager.connect(appSettingsState.getSocketBaseUrl())
                    socketManager.joinMeeting(id)
                    true
                }
                is Resource.Error -> {
                    handleMeetingError(
                        message = result.message,
                        showLoading = showLoading,
                        hasSuccessContent = hasSuccessContent,
                        hiddenMessage = hiddenMessage
                    )
                    false
                }
                Resource.Loading -> false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            if (hasSuccessContent && !showLoading) {
                _actionMessage.emit("刷新会议详情失败：${e.message ?: "未知错误"}")
            } else {
                _uiState.value = DetailUiState.Error("Error: ${e.message}")
            }
            false
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun handleMeetingError(
        message: String,
        showLoading: Boolean,
        hasSuccessContent: Boolean,
        hiddenMessage: String
    ) {
        if (message == "HTTP_404") {
            if (hasSuccessContent && !showLoading) {
                _exitDetail.emit(hiddenMessage)
            } else {
                _uiState.value = DetailUiState.Error(hiddenMessage)
            }
            return
        }

        if (hasSuccessContent && !showLoading) {
            _actionMessage.emit("刷新会议详情失败：$message")
        } else {
            _uiState.value = DetailUiState.Error(message)
        }
    }

    fun checkIn() {
        val id = meetingId?.toIntOrNull() ?: return
        val userId = currentUserIdOrNull()
        if (userId == null) {
            viewModelScope.launch {
                _actionMessage.emit("未登录，无法签到")
            }
            return
        }
        if (_isCheckInSubmitting.value) return

        viewModelScope.launch {
            try {
                _isCheckInSubmitting.value = true
                val response = repository.checkIn(userId, id)
                updateMeetingAfterCheckIn(response.id, response.checkInTime)
            } catch (e: Exception) {
                e.printStackTrace()
                _actionMessage.emit("签到失败：${extractErrorMessage(e)}")
            } finally {
                _isCheckInSubmitting.value = false
            }
        }
    }

    fun shouldShowCheckInHint(meetingId: Int): Boolean {
        val userId = currentUserIdOrNull() ?: return false
        return !userPreferences.hasSeenCheckInHint(userId, meetingId)
    }

    fun markCheckInHintSeen(meetingId: Int) {
        val userId = currentUserIdOrNull() ?: return
        userPreferences.markCheckInHintSeen(userId, meetingId)
    }

    private fun updateMeetingAfterCheckIn(checkInId: Int, checkInTime: String) {
        val current = (_uiState.value as? DetailUiState.Success)?.meeting ?: return
        _uiState.value = DetailUiState.Success(
            current.copy(
                isCheckedIn = true,
                checkInId = checkInId,
                checkInTime = checkInTime
            )
        )
    }

    private fun extractErrorMessage(exception: Exception): String {
        if (exception is HttpException) {
            val body = exception.response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                try {
                    return JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
                        ?: exception.message()
                } catch (_: Exception) {
                    return exception.message()
                }
            }
        }
        return exception.message ?: "未知错误"
    }

    private fun checkActiveVote() {
        val id = meetingId?.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                val vote = repository.getActiveVote(id, currentUserIdOrNull())
                if (vote != null) {
                    _currentVote.value = vote
                    _hasVoted.value = vote.user_voted
                    _showVoteSheet.value = true
                } else {
                    _currentVote.value = null
                    _hasVoted.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            launch {
                socketManager.voteStateChangeEvent.collectLatest { vote ->
                    val currentMeetingId = meetingId?.toIntOrNull() ?: return@collectLatest
                    if (vote.meeting_id != currentMeetingId) return@collectLatest

                    val mergedVote = mergePublicVoteState(vote)
                    _currentVote.value = mergedVote
                    _hasVoted.value = mergedVote.user_voted

                    if (mergedVote.status == "closed") {
                        fetchVoteResult(mergedVote.id)
                    } else {
                        _voteResult.value = null
                        _showVoteSheet.value = true
                    }
                }
            }
            launch {
                socketManager.voteUpdateEvent.collectLatest { data ->
                    if (_voteResult.value?.vote_id == data.vote_id) {
                        _voteResult.value = _voteResult.value?.copy(results = data.results)
                    }
                }
            }
            launch {
                socketManager.voteEndEvent.collectLatest { data ->
                    if (_currentVote.value?.id == data.vote_id) {
                        _voteResult.value = VoteResult(
                            vote_id = data.vote_id,
                            title = data.title,
                            total_voters = data.total_voters,
                            results = data.results
                        )
                        _showVoteSheet.value = true
                    }
                }
            }
            launch {
                socketManager.meetingChangedEvent.collectLatest { data ->
                    val id = meetingId?.toIntOrNull() ?: return@collectLatest
                    val isGlobalVisibilityRefresh =
                        data.action == "settings_updated" &&
                            data.setting_key == GLOBAL_VISIBILITY_SETTING_KEY

                    if (data.meeting_id == id || isGlobalVisibilityRefresh) {
                        refreshMeetingSilently(hiddenMessage = DEFAULT_HIDDEN_MESSAGE)
                    }
                }
            }
            launch {
                socketManager.connectionState.collectLatest { connected ->
                    if (connected) {
                        val id = meetingId?.toIntOrNull() ?: return@collectLatest
                        socketManager.joinMeeting(id)
                    }
                }
            }
        }
    }

    fun submitVote(optionIds: List<Int>) {
        val voteId = _currentVote.value?.id ?: return
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId != -1) {
                    repository.submitVote(voteId, userId, optionIds)
                    applyOptimisticVoteSubmission(optionIds)
                    refreshCurrentVoteFromServer(voteId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissVoteSheet() {
        _showVoteSheet.value = false
    }

    fun openVoteSheet() {
        if (_currentVote.value != null) {
            _showVoteSheet.value = true
        }
    }

    fun fetchVoteResult(voteId: Int) {
        viewModelScope.launch {
            try {
                val result = repository.getVoteResult(voteId)
                if (_currentVote.value?.id == voteId && result != null) {
                    _voteResult.value = result
                    _currentVote.value = _currentVote.value?.copy(status = "closed")
                    _showVoteSheet.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun mergePublicVoteState(incoming: Vote): Vote {
        val previousVote = _currentVote.value
        if (previousVote == null || previousVote.id != incoming.id) {
            return incoming
        }

        val preservedUserVoted = previousVote.user_voted || _hasVoted.value
        val preservedSelectedIds = when {
            incoming.selected_option_ids.isNotEmpty() -> incoming.selected_option_ids
            previousVote.selected_option_ids.isNotEmpty() -> previousVote.selected_option_ids
            else -> emptyList()
        }

        return incoming.copy(
            user_voted = incoming.user_voted || preservedUserVoted,
            selected_option_ids = preservedSelectedIds
        )
    }

    private fun applyOptimisticVoteSubmission(optionIds: List<Int>) {
        _hasVoted.value = true
        _currentVote.value = _currentVote.value?.copy(
            user_voted = true,
            selected_option_ids = optionIds
        )
    }

    private suspend fun refreshCurrentVoteFromServer(voteId: Int) {
        val userId = currentUserIdOrNull() ?: return
        try {
            repository.getVote(voteId, userId)?.let { refreshedVote ->
                val mergedVote = mergePublicVoteState(refreshedVote)
                _currentVote.value = mergedVote
                _hasVoted.value = mergedVote.user_voted
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // SocketManager is shared across screens, so it should stay connected.
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val meeting: Meeting) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
