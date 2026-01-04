package com.example.paperlessmeeting.ui.screens.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun SinglePageEditor(
    bitmap: Bitmap, // 当前页面的截图
    initialStrokes: List<AnnotationStroke>,
    onSave: (List<AnnotationStroke>) -> Unit,
    onCancel: () -> Unit
) {
    // 临时笔记状态，用于支持撤销/重做
    var strokes by remember { mutableStateOf(initialStrokes) }
    // 当前正在画的一笔 (屏幕坐标)
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    // 工具状态
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var isEraserMode by remember { mutableStateOf(false) }
    
    // 图片显示区域的大小，用于坐标转换
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Calculate aspect ratio
    val imageRatio = remember(bitmap) { 
        bitmap.width.toFloat() / bitmap.height.toFloat() 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // 编辑模式深色背景聚焦
        contentAlignment = Alignment.Center
    ) {
        // Container that strictly follows the image aspect ratio
        // This ensures that the component bounds MATCH the image visual bounds exactly.
        // No letterboxing spacing inside this box.
        Box(
            modifier = Modifier
                .padding(20.dp) // Outer margin
                .aspectRatio(imageRatio) // Force AR
                .onSizeChanged { imageSize = it }
        ) {
            // 1. 显示 PDF 当前页 (作为底图)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds, // Fill this aspect-ratio-locked box
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isEraserMode) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (!isEraserMode) currentPath = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pos = change.position
                                if (isEraserMode) {
                                    // 简单橡皮擦
                                    strokes = strokes.filterNot { stroke ->
                                        stroke.points.any { p ->
                                            val sx = p.x * size.width
                                            val sy = p.y * size.height
                                            val dist = (sx - pos.x) * (sx - pos.x) + (sy - pos.y) * (sy - pos.y)
                                            dist < 2000f // 擦除半径 squared
                                        }
                                    }
                                } else {
                                    currentPath = currentPath + pos
                                }
                            },
                            onDragEnd = {
                                if (!isEraserMode && currentPath.isNotEmpty()) {
                                    // 将屏幕坐标 转换为 归一化坐标 (0~1)
                                    val normalizedPoints = currentPath.map { offset ->
                                        NormalizedPoint(
                                            x = offset.x / size.width.toFloat(),
                                            y = offset.y / size.height.toFloat()
                                        )
                                    }
                                    
                                    val newStroke = AnnotationStroke(
                                        points = normalizedPoints,
                                        color = selectedColor.toArgb()
                                    )
                                    strokes = strokes + newStroke
                                    currentPath = emptyList()
                                }
                            }
                        )
                    }
            )

            // 2. 绘制层 (Canvas Overlay)
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 绘制已保存的线条
                strokes.forEach { stroke ->
                    val path = Path()
                    if (stroke.points.isNotEmpty()) {
                        val start = stroke.points[0]
                        path.moveTo(start.x * size.width, start.y * size.height)
                        
                        for (i in 1 until stroke.points.size) {
                            val p = stroke.points[i]
                            path.lineTo(p.x * size.width, p.y * size.height)
                        }
                        
                        drawPath(
                            path = path,
                            color = Color(stroke.color),
                            style = Stroke(
                                width = stroke.strokeWidth, 
                                cap = StrokeCap.Round, 
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 绘制当前正在画的线条
                if (currentPath.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(currentPath[0].x, currentPath[0].y)
                    for (i in 1 until currentPath.size) {
                        path.lineTo(currentPath[i].x, currentPath[i].y)
                    }
                    drawPath(
                        path = path,
                        color = selectedColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }

        // 3. 顶部工具栏 (撤销/保存)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "取消", tint = Color.White)
            }
            
            Row {
                IconButton(
                    onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                    enabled = strokes.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "撤销", tint = if(strokes.isNotEmpty()) Color.White else Color.Gray)
                }
                IconButton(onClick = { onSave(strokes) }) {
                    Icon(Icons.Default.Check, "保存", tint = Color.Green)
                }
            }
        }

        // 4. 底部工具栏 (颜色/橡皮擦)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .background(Color.DarkGray, CircleShape)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { isEraserMode = !isEraserMode }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eraser",
                    tint = if (isEraserMode) Color.Yellow else Color.White
                )
            }
            
            val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow)
            colors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { 
                            selectedColor = color 
                            isEraserMode = false
                        }
                        .border(
                            width = if (selectedColor == color && !isEraserMode) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
