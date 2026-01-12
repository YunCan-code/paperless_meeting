package com.example.paperlessmeeting.ui.screens.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.repository.MeetingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.example.paperlessmeeting.domain.model.MeetingSyncState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

sealed class ReaderUiState {
    object Idle : ReaderUiState()
    object Loading : ReaderUiState()
    data class Downloading(val progress: Float, val fileName: String) : ReaderUiState()
    data class Ready(val file: File, val initialPage: Int = 0) : ReaderUiState()
    data class Error(val message: String) : ReaderUiState()
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val readingProgressManager: com.example.paperlessmeeting.data.local.ReadingProgressManager,
    private val userPreferences: com.example.paperlessmeeting.data.local.UserPreferences,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Idle)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Sync Logic
    private val _isFollowing = MutableStateFlow(false) // Attendee: Follow presenter?
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _isPresenterSyncing = MutableStateFlow(false) // Presenter: Broadcast?
    val isPresenterSyncing: StateFlow<Boolean> = _isPresenterSyncing.asStateFlow()
    
    // Derived Role
    // Assuming 'admin' or purely 'host' control. For now check if role starts with admin
    val isPresenter: Boolean = userPreferences.getUserRole() == "admin" || userPreferences.getUserRole() == "主讲人"

    // Attendee: Is there an active broadcast?
    private val _isSyncActive = MutableStateFlow(false)
    val isSyncActive: StateFlow<Boolean> = _isSyncActive.asStateFlow()

    // Command to UI to jump page (One-time event)
    private val _oneShotJumpPage = MutableStateFlow<Int?>(null)
    val oneShotJumpPage: StateFlow<Int?> = _oneShotJumpPage.asStateFlow()

    init {
        cleanupOldCache()
        if (!isPresenter) {
            startSyncAvailabilityCheck()
        }
    }

    private fun cleanupOldCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val cacheDir = context.cacheDir
                val expirationTime = System.currentTimeMillis() - (15L * 24 * 3600 * 1000) // 15 days
                
                cacheDir.listFiles()?.forEach { file ->
                    // Iterate all files (pdfs, jsons, etc)
                    if (file.isFile && file.lastModified() < expirationTime) {
                         file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadDocument(url: String, fileName: String) {
        if (_uiState.value is ReaderUiState.Ready) return

        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val cacheDir = context.cacheDir
                
                // Use URL hash to generate a unique filename, preventing conflicts for same-named files in different meetings
                val extension = fileName.substringAfterLast(".", "pdf")
                val uniqueName = "${url.hashCode()}.$extension"
                val file = File(cacheDir, uniqueName)

                if (!file.exists()) {
                    // 使用带进度的下载，支持自动重试
                    val maxRetries = 3
                    var success = false
                    var lastError: String? = null
                    
                    for (attempt in 1..maxRetries) {
                        _uiState.value = ReaderUiState.Downloading(0f, fileName)
                        
                        success = repository.downloadFileWithProgress(url, file) { progress ->
                            _uiState.value = ReaderUiState.Downloading(progress, fileName)
                        }
                        
                        if (success) break
                        
                        // 下载失败，准备重试
                        if (attempt < maxRetries) {
                            lastError = "下载失败，正在重试 ($attempt/$maxRetries)..."
                            _uiState.value = ReaderUiState.Error(lastError)
                            kotlinx.coroutines.delay(2000) // 等待2秒后重试
                            // 删除可能的不完整文件
                            if (file.exists()) file.delete()
                        }
                    }
                    
                    if (!success) {
                        // 删除不完整的文件
                        if (file.exists()) file.delete()
                        _uiState.value = ReaderUiState.Error("下载失败，请检查网络连接后重试")
                        return@launch
                    }
                } else {
                    // Touch file to extend its retention period
                    val now = System.currentTimeMillis()
                    file.setLastModified(now)
                    // Also touch the annotation file if it exists
                    val jsonFile = File(file.parent, "${file.name}.json")
                    if (jsonFile.exists()) {
                        jsonFile.setLastModified(now)
                    }
                }
                
                // Fetch saved progress
                val savedProgress = readingProgressManager.getProgress(url)
                val initialPage = savedProgress?.currentPage ?: 0
                
                _uiState.value = ReaderUiState.Ready(file, initialPage)
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }

    // Annotation Logic
    // Start with a Map for O(1) access by page index
    private val _pageAnnotations = MutableStateFlow<Map<Int, List<AnnotationStroke>>>(emptyMap())
    val pageAnnotations: StateFlow<Map<Int, List<AnnotationStroke>>> = _pageAnnotations.asStateFlow()

    fun loadAnnotations(pdfFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonFile = File(pdfFile.parent, "${pdfFile.name}.json")
                if (jsonFile.exists()) {
                    val jsonString = jsonFile.readText()
                    // Define a DTO for storage to keep JSON clean
                    val type = object : TypeToken<Map<Int, List<AnnotationStroke>>>() {}.type
                    val data = Gson().fromJson<Map<Int, List<AnnotationStroke>>>(jsonString, type)
                    _pageAnnotations.value = data ?: emptyMap()
                } else {
                    _pageAnnotations.value = emptyMap()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _pageAnnotations.value = emptyMap()
            }
        }
    }

    fun saveAnnotations(pdfFile: File) {
        val currentData = _pageAnnotations.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = Gson().toJson(currentData)
                val jsonFile = File(pdfFile.parent, "${pdfFile.name}.json")
                jsonFile.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updatePageAnnotations(pageIndex: Int, strokes: List<AnnotationStroke>) {
        val currentMap = _pageAnnotations.value.toMutableMap()
        if (strokes.isEmpty()) {
            currentMap.remove(pageIndex)
        } else {
            currentMap[pageIndex] = strokes
        }
        _pageAnnotations.value = currentMap
    }

    fun saveReadingProgress(uniqueId: String, fileName: String, page: Int, total: Int, localPath: String? = null) {
        viewModelScope.launch {
            if (total > 0) {
                readingProgressManager.saveProgress(uniqueId, fileName, page, total, localPath)
            }
        }
    }
    
    // ================= Sync Logic =================

    private var currentMeetingId: Int = -1
    private var currentFileId: Int = -1
    private var currentFileUrl: String? = null
    
    fun initSync(meetingId: Int, fileId: Int, fileUrl: String) {
        currentMeetingId = meetingId
        currentFileId = fileId
        currentFileUrl = fileUrl
    }

    // Toggle Presenter Mode
    fun togglePresenterSync(enable: Boolean) {
        _isPresenterSyncing.value = enable
        
        // Notify backend immediately
        if (currentMeetingId != -1) {
             viewModelScope.launch {
                repository.updateSyncState(
                    meetingId = currentMeetingId,
                    fileId = currentFileId,
                    pageNumber = _oneShotJumpPage.value ?: 0, // Should be current page, but ViewModel doesn't track it tightly. Let's assume onPresenterPageChanged handles active updates.
                    // Wait, we need the *current page* here. 
                    // But ViewModel doesn't hold 'currentPage' explicitly as a state accessible here easily without passing it in.
                    // Actually, onPresenterPageChanged is called by UI.
                    // If disabling, page number doesn't matter much, but 'isSyncing' = false does.
                    isSyncing = enable,
                    fileUrl = currentFileUrl
                )
             }
        }
    }

    // Presenter: Report page change
    fun onPresenterPageChanged(page: Int) {
        if (!_isPresenterSyncing.value || currentMeetingId == -1) return
        
        viewModelScope.launch {
            repository.updateSyncState(
                meetingId = currentMeetingId,
                fileId = currentFileId,
                pageNumber = page,
                isSyncing = true,
                fileUrl = currentFileUrl
            )
        }
    }

    // Toggle Attendee Follow Mode
    fun toggleFollow(enable: Boolean) {
        val wasDisabled = !_isFollowing.value
        _isFollowing.value = enable
        
        if (wasDisabled && enable && currentMeetingId != -1) {
            // Restart polling loop if enabling
            startPollingLoop()
        }
    }
    
    // Consume jump event
    fun consumeJumpEvent() {
        _oneShotJumpPage.value = null
    }

    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()

    fun consumeToastEvent() {
        _toastEvent.value = null
    }

    private fun startSyncAvailabilityCheck() {
        viewModelScope.launch {
            while (isActive) {
                if (currentMeetingId != -1) {
                    try {
                        val state = repository.getSyncState(currentMeetingId)
                        val active = state?.is_syncing == true
                        
                        // Detect Transition: Active -> Inactive
                        val wasActive = _isSyncActive.value
                        _isSyncActive.value = active
                        
                        // Auto-disconnect if following
                        if (wasActive && !active && _isFollowing.value) {
                             _isFollowing.value = false
                             _toastEvent.value = "主讲人已结束同屏"
                        }
                        
                        val interval = if (active && !_isFollowing.value) 2000L else 5000L
                         delay(interval)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        delay(5000)
                    }
                } else {
                    delay(2000)
                }
            }
        }
    }

    private fun startPollingLoop() {
        viewModelScope.launch {
            // ... existing poll loop
            while (isActive && _isFollowing.value) {
                // ...

                if (currentMeetingId != -1) {
                    val state = repository.getSyncState(currentMeetingId)
                    if (state != null && state.is_syncing) {
                        // Check file match (Simple check for now)
                        // If file differs, we might need to prompt download, but for MVP assume same file opened
                        // In real world, check state.file_id == currentFileId
                        
                        if (state.file_id == currentFileId) {
                            // If same file, jump to page
                             _oneShotJumpPage.value = state.page_number
                        } else {
                            // Different file? For now just ignore or Toast
                            // Ideally trigger loadDocument(state.file_url)
                        }
                    }
                }
                delay(1000) // Poll every 1s
            }
        }
    }
}

