package com.example.paperlessmeeting.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.data.local.ReadingProgress

private enum class PdfThumbnailState {
    Loading,
    Success,
    Error
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecentReadingCard(
    progress: ReadingProgress,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isDeleting: Boolean,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    fixedHeight: Dp? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    val cardWidth = if (isPhone) 248.dp else 272.dp
    val cardHeight = fixedHeight ?: if (isPhone) 108.dp else 110.dp
    val defaultContainerColor = MaterialTheme.colorScheme.surface
    val selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val thumbnailContainerColor = MaterialTheme.colorScheme.surface

    val cardScale by animateFloatAsState(
        targetValue = when {
            isDeleting -> 0.9f
            isSelected -> 0.98f
            else -> 1f
        },
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.72f),
        label = "recent_reading_scale"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = when {
            isDeleting -> 0f
            isSelectionMode && !isSelected -> 0.72f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 180),
        label = "recent_reading_alpha"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            selectedContainerColor
        } else {
            defaultContainerColor
        },
        animationSpec = tween(durationMillis = 180),
        label = "recent_reading_container_color"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
        },
        animationSpec = tween(durationMillis = 180),
        label = "recent_reading_border_color"
    )

    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth))
            .height(cardHeight)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = cardAlpha
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (isSelected) 1.5.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .combinedClickable(
                    onClick = {
                        if (!isDeleting) onClick()
                    },
                    onLongClick = {
                        if (!isDeleting) onLongClick()
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .width(62.dp)
                        .fillMaxHeight()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    shape = RoundedCornerShape(10.dp),
                    color = thumbnailContainerColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val localFile = progress.localPath?.let { java.io.File(it) }
                        val hasLocalFile = localFile?.exists() == true &&
                            localFile.isFile &&
                            localFile.length() > 0L

                        if (hasLocalFile) {
                            PdfThumbnail(
                                filePath = progress.localPath!!,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Top
                ) {
                    // 标题固定占 2 行高度，避免进度条随行数浮动
                    Text(
                        text = progress.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    val progressPercent = if (progress.totalPages > 0) {
                        (progress.currentPage + 1).toFloat() / progress.totalPages.toFloat()
                    } else {
                        0f
                    }

                    LinearProgressIndicator(
                        progress = { progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "上次阅读至：第 ${progress.currentPage + 1} 页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSelected && !isDeleting,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            enter = fadeIn(animationSpec = tween(140)) + scaleIn(animationSpec = tween(180), initialScale = 0.6f),
            exit = fadeOut(animationSpec = tween(120)) + scaleOut(animationSpec = tween(120), targetScale = 0.6f)
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选中最近阅读",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PdfThumbnail(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var thumbnailState by remember(filePath) { mutableStateOf(PdfThumbnailState.Loading) }

    LaunchedEffect(filePath) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(filePath)
                if (!file.exists() || !file.isFile || file.length() <= 0L) {
                    thumbnailState = PdfThumbnailState.Error
                    return@withContext
                }

                val fileDescriptor = android.os.ParcelFileDescriptor.open(
                    file,
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                )
                val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                val page = renderer.openPage(0)

                val width = 150
                val height = (width * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bmp = android.graphics.Bitmap.createBitmap(
                    width,
                    height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(
                    bmp,
                    null,
                    null,
                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                page.close()
                renderer.close()
                fileDescriptor.close()

                bitmap = bmp
                thumbnailState = PdfThumbnailState.Success
            } catch (e: Exception) {
                bitmap = null
                thumbnailState = PdfThumbnailState.Error
                android.util.Log.w(
                    "RecentReadingCard",
                    "PdfThumbnail: unable to render thumbnail for $filePath"
                )
            }
        }
    }

    if (thumbnailState == PdfThumbnailState.Success && bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else if (thumbnailState == PdfThumbnailState.Error) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

