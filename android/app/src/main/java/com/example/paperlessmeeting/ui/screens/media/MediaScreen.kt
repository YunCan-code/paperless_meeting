package com.example.paperlessmeeting.ui.screens.media

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.paperlessmeeting.domain.model.MediaItem
import com.example.paperlessmeeting.ui.components.image.AppAsyncImage
import com.example.paperlessmeeting.ui.components.image.MediaImageResolver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FolderBlue = Color(0xFF5AC8FA)
private val FolderBlueDark = Color(0xFF4AB8EA)
private val FolderBlueLight = Color(0xFFB8E8FF)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaScreen(
    isActive: Boolean = true,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val staticBaseUrl = viewModel.staticBaseUrl
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewVideoItem by remember { mutableStateOf<MediaItem?>(null) }
    var previewImageItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var previewImageIndex by remember { mutableStateOf<Int?>(null) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isPhone = screenWidthDp < 600
    val hPadding = if (isPhone) 16.dp else 28.dp
    val vPadding = if (isPhone) 16.dp else 24.dp

    LaunchedEffect(isActive) {
        if (isActive) {
            viewModel.refreshOnVisible()
        }
    }

    DisposableEffect(lifecycleOwner, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActive) {
                viewModel.refreshOnVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(
        enabled = previewImageIndex != null ||
            previewVideoItem != null ||
            uiState.currentFolderId != null
    ) {
        when {
            previewImageIndex != null -> {
                previewImageIndex = null
                previewImageItems = emptyList()
            }
            previewVideoItem != null -> {
                previewVideoItem = null
            }
            uiState.currentFolderId != null -> {
                viewModel.goToParent()
            }
        }
    }

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
                                staticBaseUrl = staticBaseUrl,
                                onClick = {
                                    when (item.kind) {
                                        "folder" -> viewModel.navigateToFolder(item.id)
                                        "image" -> {
                                            val images = uiState.items.filter { it.kind == "image" }
                                            val idx = images.indexOfFirst { it.id == item.id }
                                            if (idx >= 0) {
                                                previewImageItems = images
                                                previewImageIndex = idx
                                            }
                                        }
                                        "video" -> previewVideoItem = item
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

    if (previewImageIndex != null && previewImageItems.isNotEmpty()) {
        MediaImagePagerDialog(
            items = previewImageItems,
            startIndex = previewImageIndex ?: 0,
            staticBaseUrl = staticBaseUrl,
            onDismiss = {
                previewImageIndex = null
                previewImageItems = emptyList()
            }
        )
    }

    if (previewVideoItem != null) {
        MediaVideoPreviewDialog(
            item = previewVideoItem!!,
            staticBaseUrl = staticBaseUrl,
            onDismiss = { previewVideoItem = null }
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
    staticBaseUrl: String,
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
            "image" -> ImageThumbnail(item, staticBaseUrl)
            "video" -> VideoThumbnail(item, staticBaseUrl)
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
private fun ImageThumbnail(item: MediaItem, staticBaseUrl: String) {
    val imageModel = remember(item.id, item.thumbnailUrl, item.previewUrl, staticBaseUrl) {
        MediaImageResolver.resolveGrid(item, staticBaseUrl)
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
            AppAsyncImage(
                model = imageModel,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ==================== Video Thumbnail ====================

@Composable
@Suppress("UNUSED_PARAMETER")
private fun VideoThumbnail(item: MediaItem, staticBaseUrl: String) {
    val imageModel = remember(item.id, item.thumbnailUrl, item.previewUrl, staticBaseUrl) {
        MediaImageResolver.resolveGrid(item, staticBaseUrl)
    }

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
            AppAsyncImage(
                model = imageModel,
                modifier = Modifier.fillMaxSize()
            )

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

// ==================== Preview Dialogs ====================

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaImagePagerDialog(
    items: List<MediaItem>,
    startIndex: Int,
    staticBaseUrl: String,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                val imageModel = remember(item.id, item.previewUrl, item.thumbnailUrl, staticBaseUrl) {
                    MediaImageResolver.resolveFullscreen(item, staticBaseUrl)
                }

                AppAsyncImage(
                    model = imageModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                )
            }

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

            val currentItem = items.getOrNull(pagerState.currentPage)
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
                Column {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    if (currentItem != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentItem.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaVideoPreviewDialog(
    item: MediaItem,
    staticBaseUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val context = LocalContext.current
        val videoUrl = item.previewUrl?.takeIf { it.isNotEmpty() }?.let {
            staticBaseUrl.trimEnd('/') + it.removePrefix("/static")
        }
        val player = remember(videoUrl) {
            ExoPlayer.Builder(context).build().apply {
                if (videoUrl != null) {
                    setMediaItem(ExoMediaItem.fromUri(videoUrl))
                    prepare()
                }
            }
        }
        DisposableEffect(player) {
            onDispose { player.release() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            if (videoUrl != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
            } else {
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
