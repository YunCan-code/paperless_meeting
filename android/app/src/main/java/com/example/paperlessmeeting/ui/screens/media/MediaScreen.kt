package com.example.paperlessmeeting.ui.screens.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.paperlessmeeting.BuildConfig
import com.example.paperlessmeeting.domain.model.MediaItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FolderBlue = Color(0xFF5AC8FA)
private val FolderBlueDark = Color(0xFF4AB8EA)
private val FolderBlueLight = Color(0xFFB8E8FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            // Header — GoodNotes style large title
            Text(
                text = if (uiState.currentFolderId == null) "媒体" else uiState.breadcrumbs.lastOrNull()?.title ?: "媒体",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Breadcrumbs (only when inside a folder)
            if (uiState.currentFolderId != null) {
                BreadcrumbBar(
                    uiState = uiState,
                    onRootClick = { viewModel.goToRoot() },
                    onCrumbClick = { viewModel.goToBreadcrumb(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Centered capsule filter bar
            FilterCapsuleBar(
                activeFilter = uiState.activeFilter,
                onFilterChange = { viewModel.setFilter(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "加载失败",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "此目录暂无内容",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            MediaGridItem(
                                item = item,
                                onClick = {
                                    when (item.kind) {
                                        "folder" -> viewModel.navigateToFolder(item.id)
                                        "image" -> previewItem = item
                                        "video" -> previewItem = item
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating back button when inside a folder
        if (uiState.currentFolderId != null) {
            IconButton(
                onClick = {
                    val crumbs = uiState.breadcrumbs
                    if (crumbs.size >= 2) {
                        viewModel.goToBreadcrumb(crumbs[crumbs.size - 2])
                    } else {
                        viewModel.goToRoot()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(28.dp)
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    if (previewItem != null) {
        MediaPreviewDialog(
            item = previewItem!!,
            onDismiss = { previewItem = null }
        )
    }
}

// ==================== Breadcrumbs ====================

@Composable
private fun BreadcrumbBar(
    uiState: MediaUiState,
    onRootClick: () -> Unit,
    onCrumbClick: (com.example.paperlessmeeting.domain.model.MediaBreadcrumb) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "媒体库",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onRootClick() }
        )

        uiState.breadcrumbs.forEachIndexed { index, crumb ->
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
            val isLast = index == uiState.breadcrumbs.lastIndex
            Text(
                text = crumb.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast)
                    MaterialTheme.colorScheme.onBackground
                else
                    MaterialTheme.colorScheme.primary,
                modifier = if (!isLast) Modifier.clickable { onCrumbClick(crumb) } else Modifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== Capsule Filter Bar ====================

@Composable
private fun FilterCapsuleBar(
    activeFilter: String,
    onFilterChange: (String) -> Unit
) {
    val filters = listOf(
        "all" to "全部",
        "image" to "图片",
        "video" to "视频"
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 0.dp
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                filters.forEach { (key, label) ->
                    val isSelected = activeFilter == key
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.surface
                        else
                            Color.Transparent,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onFilterChange(key) }
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== Grid Item ====================

@Composable
private fun MediaGridItem(
    item: MediaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thumbnail
        when (item.kind) {
            "folder" -> FolderThumbnail()
            "image" -> ImageThumbnail(item)
            "video" -> VideoThumbnail(item)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )

        // Date
        val dateText = formatItemDate(item.updatedAt)
        if (dateText != null) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== Folder Thumbnail (GoodNotes style) ====================

@Composable
private fun FolderThumbnail() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height
            val r = w * 0.08f

            // Back panel (slightly larger, offset up)
            val backPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(0f, h * 0.08f),
                            size = Size(w, h * 0.88f)
                        ),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }
            drawPath(backPath, color = FolderBlueDark)

            // Tab on top-left
            val tabPath = Path().apply {
                moveTo(0f, h * 0.15f + r)
                quadraticBezierTo(0f, h * 0.15f, r, h * 0.15f)
                lineTo(w * 0.35f, h * 0.15f)
                quadraticBezierTo(w * 0.40f, h * 0.15f, w * 0.42f, h * 0.22f)
                lineTo(w - r, h * 0.22f)
                quadraticBezierTo(w, h * 0.22f, w, h * 0.22f + r)
                lineTo(w, h - r)
                quadraticBezierTo(w, h, w - r, h)
                lineTo(r, h)
                quadraticBezierTo(0f, h, 0f, h - r)
                close()
            }
            drawPath(tabPath, color = FolderBlue)

            // Front panel highlight (lighter strip at top of body)
            val highlightPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(w * 0.04f, h * 0.24f),
                            size = Size(w * 0.92f, h * 0.12f)
                        ),
                        cornerRadius = CornerRadius(r * 0.5f, r * 0.5f)
                    )
                )
            }
            drawPath(highlightPath, color = FolderBlueLight.copy(alpha = 0.5f))
        }
    }
}

// ==================== Image Thumbnail ====================

@Composable
private fun ImageThumbnail(item: MediaItem) {
    val imageUrl = item.previewUrl?.takeIf { it.isNotEmpty() }?.let {
        BuildConfig.STATIC_BASE_URL.trimEnd('/') + it.removePrefix("/static")
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ==================== Video Thumbnail ====================

@Composable
@Suppress("UNUSED_PARAMETER")
private fun VideoThumbnail(item: MediaItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F0FE))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.VideoFile,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = Color(0xFF4285F4).copy(alpha = 0.6f)
            )
            // Play badge
            Icon(
                Icons.Default.PlayCircleFilled,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp),
                tint = Color(0xFF4285F4)
            )
        }
    }
}

// ==================== Date Formatter ====================

private fun formatItemDate(dateStr: String?): String? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val clean = dateStr.replace(" ", "T")
        val dt = LocalDateTime.parse(clean.substringBefore("."))
        dt.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA))
    } catch (_: Exception) {
        null
    }
}

// ==================== Preview Dialog ====================

@Composable
private fun MediaPreviewDialog(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            when (item.kind) {
                "image" -> {
                    val imageUrl = item.previewUrl?.takeIf { it.isNotEmpty() }?.let {
                        BuildConfig.STATIC_BASE_URL.trimEnd('/') + it.removePrefix("/static")
                    }

                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                "video" -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PlayCircleFilled,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }

            // Title at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .padding(24.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
