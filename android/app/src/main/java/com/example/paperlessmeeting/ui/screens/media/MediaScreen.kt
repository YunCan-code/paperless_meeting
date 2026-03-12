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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircleFilled
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
import androidx.compose.runtime.LaunchedEffect
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.paperlessmeeting.BuildConfig
import com.example.paperlessmeeting.domain.model.MediaItem
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
    val hPadding = if (isPhone) 16.dp else 28.dp
    val vPadding = if (isPhone) 16.dp else 24.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hPadding, vertical = vPadding)
        ) {
            Text(
                text = if (uiState.currentFolderId == null) "媒体" else uiState.breadcrumbs.lastOrNull()?.title ?: "媒体",
                style = if (isPhone) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(if (isPhone) 14.dp else 20.dp))

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

            Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))

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
                        columns = GridCells.Adaptive(minSize = if (isPhone) 100.dp else 150.dp),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 20.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isPhone) 16.dp else 24.dp),
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

                        // Load more trigger
                        if (uiState.hasMore || uiState.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                // Trigger load more when this item becomes visible
                                LaunchedEffect(uiState.currentPage) {
                                    viewModel.loadMore()
                                }
                            }
                        }
                    }
                }
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
            .aspectRatio(1.25f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = w * 0.06f

            // Shadow
            val shadowPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(w * 0.02f, h * 0.22f),
                            size = Size(w * 0.96f, h * 0.78f)
                        ),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }
            drawPath(shadowPath, color = FolderBlueDark.copy(alpha = 0.15f))

            // Back panel
            val backPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(w * 0.02f, h * 0.12f),
                            size = Size(w * 0.96f, h * 0.82f)
                        ),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }
            drawPath(backPath, color = FolderBlueDark)

            // Tab
            val tabW = w * 0.38f
            val tabPath = Path().apply {
                moveTo(w * 0.06f, h * 0.12f)
                lineTo(w * 0.06f, h * 0.04f + r * 0.6f)
                quadraticBezierTo(w * 0.06f, h * 0.04f, w * 0.06f + r * 0.6f, h * 0.04f)
                lineTo(w * 0.06f + tabW - r, h * 0.04f)
                quadraticBezierTo(w * 0.06f + tabW, h * 0.04f, w * 0.06f + tabW + r * 0.4f, h * 0.12f)
                close()
            }
            drawPath(tabPath, color = FolderBlueDark)

            // Front body
            val bodyPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(0f, h * 0.20f),
                            size = Size(w, h * 0.80f)
                        ),
                        cornerRadius = CornerRadius(r, r)
                    )
                )
            }
            drawPath(bodyPath, color = FolderBlue)

            // Top edge highlight
            drawRoundRect(
                color = FolderBlueLight.copy(alpha = 0.6f),
                topLeft = Offset(w * 0.04f, h * 0.22f),
                size = Size(w * 0.92f, h * 0.06f),
                cornerRadius = CornerRadius(r * 0.4f, r * 0.4f)
            )
        }
    }
}

// ==================== Image Thumbnail ====================

@Composable
private fun ImageThumbnail(item: MediaItem) {
    // Prefer thumbnailUrl for grid view, fall back to previewUrl (original image)
    val rawUrl = (item.thumbnailUrl?.takeIf { it.isNotEmpty() } ?: item.previewUrl)
        ?.takeIf { it.isNotEmpty() }
    val imageUrl = rawUrl?.let {
        BuildConfig.STATIC_BASE_URL.trimEnd('/') + it.removePrefix("/static")
    }
    val context = LocalContext.current

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
                val imageRequest = remember(context, imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(false)
                        .allowHardware(true)
                        .precision(Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
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
