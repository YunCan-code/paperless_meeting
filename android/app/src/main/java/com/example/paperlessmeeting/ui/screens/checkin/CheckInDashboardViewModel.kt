package com.example.paperlessmeeting.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.local.UserPreferences
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckInDashboardUiState(
    val isLoading: Boolean = true,
    val stats: DashboardStats = DashboardStats(),
    val todayStatus: TodayStatusResponse? = null,
    val collaborators: List<Collaborator> = emptyList(),
    val typeDistribution: List<TypeDistributionItem> = emptyList(),
    val heatmap: Map<String, Int> = emptyMap(),
    val history: List<CheckInHistoryItem> = emptyList(),
    val selectedRange: String = "month",
    val error: String? = null,
    val actionMessage: String? = null // 操作反馈（签到成功、取消等）
)

@HiltViewModel
class CheckInDashboardViewModel @Inject constructor(
    private val apiService: ApiService,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInDashboardUiState())
    val uiState: StateFlow<CheckInDashboardUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = null)
            }
            val userId = userPreferences.getUserId()
            if (userId == -1) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "未登录")
                return@launch
            }
            try {
                val range = _uiState.value.selectedRange
                val stats = apiService.getDashboardStats(userId, range)
                val today = apiService.getTodayStatus(userId)
                val collabs = apiService.getCollaborators(userId)
                val types = apiService.getTypeDistribution(userId, range)
                val heatmap = apiService.getHeatmap(userId)
                val history = apiService.getCheckinHistory(userId)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    stats = stats,
                    todayStatus = today,
                    collaborators = collabs.collaborators,
                    typeDistribution = types.distribution,
                    heatmap = heatmap.heatmap,
                    history = history
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun switchRange(range: String) {
        _uiState.value = _uiState.value.copy(selectedRange = range)
        loadAll(showLoading = false)
    }

    fun checkIn(meetingId: Int) {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            try {
                apiService.checkIn(CheckInRequest(userId = userId, meetingId = meetingId))
                _uiState.value = _uiState.value.copy(actionMessage = "签到成功!")
                loadAll(showLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionMessage = "签到失败: ${e.message}")
            }
        }
    }

    fun cancelCheckIn(checkinId: Int) {
        viewModelScope.launch {
            try {
                apiService.cancelCheckIn(checkinId)
                _uiState.value = _uiState.value.copy(actionMessage = "已取消打卡")
                loadAll(showLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionMessage = "取消失败: ${e.message}")
            }
        }
    }

    fun makeupCheckIn(meetingId: Int, remark: String?) {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            try {
                apiService.makeupCheckIn(MakeupRequest(userId = userId, meetingId = meetingId, remark = remark))
                _uiState.value = _uiState.value.copy(actionMessage = "补签成功!")
                loadAll(showLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(actionMessage = "补签失败: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }
}
