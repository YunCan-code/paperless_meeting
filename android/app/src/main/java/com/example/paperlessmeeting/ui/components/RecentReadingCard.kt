package com.example.paperlessmeeting.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperlessmeeting.data.local.ReadingProgress

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecentReadingCard(
    progress: ReadingProgress,
    showDeleteAction: Boolean,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recent_reading_wiggle")
    val wiggleRotation by infiniteTransition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 110),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recent_reading_rotation"
    )

    val cardScale by animateFloatAsState(
        targetValue = when {
            isDeleting -> 0.82f
            showDeleteAction -> 0.97f
            else -> 1f
        },
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.72f),
        label = "recent_reading_scale"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "recent_reading_alpha"
    )

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(110.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
                alpha = cardAlpha
                rotationZ = if (showDeleteAction && !isDeleting) wiggleRotation else 0f
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        if (!isDeleting) onClick()
                    },
                    onLongClick = {
                        if (!isDeleting) onLongClick()
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = if (showDeleteAction) 6.dp else 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    val hasLocalFile = !progress.localPath.isNullOrEmpty() &&
                        java.io.File(progress.localPath).exists()

                    if (hasLocalFile) {
                        PdfThumbnail(
                            filePath = progress.localPath!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = progress.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "上次阅读至：第 ${progress.currentPage + 1} 页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showDeleteAction && !isDeleting,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            enter = fadeIn(animationSpec = tween(140)) + scaleIn(animationSpec = tween(180), initialScale = 0.6f),
            exit = fadeOut(animationSpec = tween(120)) + scaleOut(animationSpec = tween(120), targetScale = 0.6f)
        ) {
            FilledIconButton(
                onClick = {
                    if (!isDeleting) onDeleteClick()
                },
                modifier = Modifier.size(24.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除最近阅读",
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun PdfThumbnail(
    filePath: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(filePath) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    val fileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
                    val page = renderer.openPage(0)

                    val width = 150
                    val height = (width * page.height / page.width.toFloat()).toInt()
                    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    page.close()
                    renderer.close()
                    fileDescriptor.close()

                    bitmap = bmp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}
