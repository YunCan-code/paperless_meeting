package com.example.paperlessmeeting.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.AppSettingsState
import com.example.paperlessmeeting.data.remote.SocketManager
import com.example.paperlessmeeting.domain.model.Vote
import com.example.paperlessmeeting.domain.model.VoteResult
import com.example.paperlessmeeting.domain.model.VoteSubmitRequest
import com.example.paperlessmeeting.data.remote.VoteUpdateData
import com.example.paperlessmeeting.data.remote.VoteEndData
import kotlinx.coroutines.flow.collectLatest
import com.example.paperlessmeeting.data.repository.MeetingRepository
import com.example.paperlessmeeting.domain.model.Meeting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val socketManager: SocketManager,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    private val appSettingsState: AppSettingsState,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val GLOBAL_VISIBILITY_SETTING_KEY = "meeting_visibility_hide_after_hours"
    }

    private val meetingId: String? = savedStateHandle["meetingId"]

    val staticBaseUrl: String get() = appSettingsState.getStaticBaseUrl()
    
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()
    private val _actionMessage = MutableSharedFlow<String>()
    val actionMessage: SharedFlow<String> = _actionMessage.asSharedFlow()
    private val _exitDetail = MutableSharedFlow<String>()
    val exitDetail: SharedFlow<String> = _exitDetail.asSharedFlow()
    private val _isCheckInSubmitting = MutableStateFlow(false)
    val isCheckInSubmitting: StateFlow<Boolean> = _isCheckInSubmitting.asStateFlow()

    // Voting State
    private val _currentVote = MutableStateFlow<Vote?>(null)
    val currentVote: StateFlow<Vote?> = _currentVote.asStateFlow()

    private val _voteResult = MutableStateFlow<VoteResult?>(null)
    val voteResult: StateFlow<VoteResult?> = _voteResult.asStateFlow()

    private val _hasVoted = MutableStateFlow(false)
    val hasVoted: StateFlow<Boolean> = _hasVoted.asStateFlow()

    private val _showVoteSheet = MutableStateFlow(false)
    val showVoteSheet: StateFlow<Boolean> = _showVoteSheet.asStateFlow()

    init {
        loadMeeting()
        observeSocketEvents()
        checkActiveVote()
    }

    private fun currentUserIdOrNull(): Int? = userPreferences.getUserId().takeIf { it > 0 }

    private fun loadMeeting() {
        viewModelScope.launch {
            try {
                val id = meetingId?.toIntOrNull()
                if (id == null) {
                    _uiState.value = DetailUiState.Error("Invalid Meeting ID")
                    return@launch
                }
                val result = repository.getMeetingById(id, currentUserIdOrNull())
                if (result is com.example.paperlessmeeting.utils.Resource.Success) {
                    _uiState.value = DetailUiState.Success(result.data)
                    
                    // Connect Socket when meeting is loaded
                    socketManager.connect(appSettingsState.getSocketBaseUrl())
                    socketManager.joinMeeting(id)
                } else if (result is com.example.paperlessmeeting.utils.Resource.Error) {
                    _uiState.value = DetailUiState.Error(
                        if (result.message == "HTTP_404") "当前会议暂不可见" else result.message
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = DetailUiState.Error("Error: ${e.message}")
            }
        }
    }

    fun checkIn() {
        val id = meetingId?.toIntOrNull() ?: return
        val userId = currentUserIdOrNull()
        if (userId == null) {
            viewModelScope.launch { _actionMessage.emit("未登录，无法签到") }
            return
        }
        if (_isCheckInSubmitting.value) return

        viewModelScope.launch {
            try {
                _isCheckInSubmitting.value = true
                repository.checkIn(userId, id)
                _actionMessage.emit("签到成功")
                refreshMeetingStateAfterCheckIn()
            } catch (e: Exception) {
                e.printStackTrace()
                _actionMessage.emit("签到失败：${e.message ?: "未知错误"}")
            } finally {
                _isCheckInSubmitting.value = false
            }
        }
    }

    fun cancelCheckIn() {
        val currentMeeting = (_uiState.value as? DetailUiState.Success)?.meeting ?: return
        val checkInId = currentMeeting.checkInId ?: return
        val userId = currentUserIdOrNull()
        if (userId == null) {
            viewModelScope.launch { _actionMessage.emit("未登录，无法取消签到") }
            return
        }
        if (_isCheckInSubmitting.value) return

        viewModelScope.launch {
            try {
                _isCheckInSubmitting.value = true
                repository.cancelCheckIn(checkInId, userId)
                val result = repository.getMeetingById(currentMeeting.id, userId)
                when (result) {
                    is com.example.paperlessmeeting.utils.Resource.Success -> {
                        _uiState.value = DetailUiState.Success(result.data)
                        _actionMessage.emit("已取消签到")
                    }
                    is com.example.paperlessmeeting.utils.Resource.Error -> {
                        if (result.message == "HTTP_404") {
                            _exitDetail.emit("取消签到后，该会议已被隐藏，已返回上一页")
                        } else {
                            _actionMessage.emit("取消签到后刷新失败：${result.message}")
                            loadMeeting()
                        }
                    }
                    else -> Unit
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _actionMessage.emit("取消签到失败：${e.message ?: "未知错误"}")
            } finally {
                _isCheckInSubmitting.value = false
            }
        }
    }

    private suspend fun refreshMeetingStateAfterCheckIn() {
        val id = meetingId?.toIntOrNull() ?: return
        when (val result = repository.getMeetingById(id, currentUserIdOrNull())) {
            is com.example.paperlessmeeting.utils.Resource.Success -> {
                _uiState.value = DetailUiState.Success(result.data)
            }
            is com.example.paperlessmeeting.utils.Resource.Error -> {
                _uiState.value = DetailUiState.Error(
                    if (result.message == "HTTP_404") "当前会议暂不可见" else result.message
                )
            }
            else -> Unit
        }
    }

    private fun checkActiveVote() {
        val id = meetingId?.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                // Assuming apiService is available via repository, or need to add it to Repo
                // If repository doesn't expose it, we might need to update Repository first.
                // Let's check repository... (Wait, I only updated ApiService, not Repository)
                // We'll update Repository in next step or use direct call if injected (but structure demands Repo)
                // TEMPORARY: using repository.apiService if public, or assuming updated Repo.
                // Since I cannot update Repo in this turn easily without seeing it, I will assume 
                // I need to update Repository first. But let's write the VM logic and then update Repo.
                val vote = repository.getActiveVote(id)
                if (vote != null) {
                    _currentVote.value = vote
                    _showVoteSheet.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeSocketEvents() {
        viewModelScope.launch {
            launch {
                socketManager.voteStartEvent.collectLatest { vote ->
                    _currentVote.value = vote
                    _voteResult.value = null
                    _hasVoted.value = false
                    _showVoteSheet.value = true
                }
            }
            launch {
                socketManager.voteUpdateEvent.collectLatest { data ->
                    // Real-time updates if sheet is open?
                    // Currently UI shows result only after vote end or if voted.
                    // We can update result if we have it
                    if (_voteResult.value != null && _voteResult.value?.vote_id == data.vote_id) {
                         // Update partial result logic if needed, but usually we wait for end or user vote
                         // If we want real-time bar chart update:
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
                         _showVoteSheet.value = true // Ensure sheet is up to show result
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
                        loadMeeting()
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
        // Need user ID. Assuming saved in Prefs. 
        // For simplicity let's assume Repository can handle or pass a dummy ID for now if Auth not fully wired
        // Actually UserPreferences is available.
        // Let's pass to repository
        viewModelScope.launch {
            try {
                val userId = userPreferences.getUserId()
                if (userId != -1) {
                    repository.submitVote(voteId, userId, optionIds)
                    _hasVoted.value = true
                }
            } catch (e: Exception) {
                // Handle error
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
                if (_currentVote.value?.id == voteId) {
                    _voteResult.value = result
                    _currentVote.value = _currentVote.value?.copy(status = "closed")
                    _showVoteSheet.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // SocketManager is a Singleton shared by multiple screens.
        // Do not disconnect here, otherwise other active screens lose realtime updates.
    }
}

sealed class DetailUiState {
    object Loading : DetailUiState()
    data class Success(val meeting: Meeting) : DetailUiState()
    data class Error(val message: String) : DetailUiState()
}
