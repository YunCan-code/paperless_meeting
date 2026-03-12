package com.example.paperlessmeeting.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paperlessmeeting.data.remote.ApiService
import com.example.paperlessmeeting.domain.model.MediaBreadcrumb
import com.example.paperlessmeeting.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PAGE_SIZE = 40

data class MediaUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<MediaItem> = emptyList(),
    val breadcrumbs: List<MediaBreadcrumb> = emptyList(),
    val currentFolderId: Int? = null,
    val error: String? = null,
    val activeFilter: String = "all",
    val hasMore: Boolean = false,
    val currentPage: Int = 0
)

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadItems()
    }

    fun loadItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentPage = 0, hasMore = false) }
            try {
                val kind = _uiState.value.activeFilter.takeIf { it != "all" }
                val page = api.getMediaItems(
                    parentId = _uiState.value.currentFolderId,
                    kind = kind,
                    visibleOnAndroid = true,
                    skip = 0,
                    limit = PAGE_SIZE
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = page.items,
                        hasMore = page.skip + page.items.size < page.total,
                        currentPage = 0
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextPage = state.currentPage + 1
                val skip = nextPage * PAGE_SIZE
                val kind = state.activeFilter.takeIf { it != "all" }
                val page = api.getMediaItems(
                    parentId = state.currentFolderId,
                    kind = kind,
                    visibleOnAndroid = true,
                    skip = skip,
                    limit = PAGE_SIZE
                )
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + page.items,
                        hasMore = skip + page.items.size < page.total,
                        currentPage = nextPage
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun navigateToFolder(folderId: Int?) {
        _uiState.update { it.copy(currentFolderId = folderId) }
        loadItems()
        loadBreadcrumbs()
    }

    fun goToRoot() {
        _uiState.update { it.copy(currentFolderId = null, breadcrumbs = emptyList()) }
        loadItems()
    }

    fun goToBreadcrumb(crumb: MediaBreadcrumb) {
        _uiState.update { it.copy(currentFolderId = crumb.id) }
        loadItems()
        loadBreadcrumbs()
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(activeFilter = filter) }
        loadItems()
    }

    private fun loadBreadcrumbs() {
        val folderId = _uiState.value.currentFolderId ?: return
        viewModelScope.launch {
            try {
                val ancestors = api.getMediaAncestors(folderId)
                _uiState.update { it.copy(breadcrumbs = ancestors) }
            } catch (_: Exception) { }
        }
    }

    fun refresh() {
        loadItems()
        if (_uiState.value.currentFolderId != null) {
            loadBreadcrumbs()
        }
    }
}
