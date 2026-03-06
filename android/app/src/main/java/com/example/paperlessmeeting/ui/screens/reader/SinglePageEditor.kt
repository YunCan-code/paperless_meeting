package com.example.paperlessmeeting.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OverlayIconGrey = Color(0xFF5F6368)
private val OverlayFloatingSurface = Color.White.copy(alpha = 0.95f)

private val PenColors = listOf(
    Color(0xFFE53935),
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFF212121),
)

private val HighlighterColors = listOf(
    Color(0xFFFFEB3B),
    Color(0xFF76FF03),
    Color(0xFF40C4FF),
    Color(0xFFFF80AB),
)

private const val HIGHLIGHTER_ALPHA = 0.35f
private const val HIGHLIGHTER_WIDTH = 24f

private enum class StrokeSize(val width: Float, val dotSize: Float) {
    THIN(3f, 6f),
    MEDIUM(6f, 10f),
    THICK(12f, 16f),
}

private enum class ToolMode { PEN, HIGHLIGHTER, ERASER }

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    indicatorColor: Color,
    onClick: () -> Unit
) {
    val tint = if (isSelected) indicatorColor else OverlayIconGrey
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) indicatorColor.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier
            .width(40.dp)
            .height(44.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = tint,
                maxLines = 1
            )
        }
    }
}

/**
 * Transparent overlay for inline PDF annotation.
 * Placed directly on top of PDFView — captures all touch events to draw/erase strokes
 * while preventing the PDFView from scrolling or zooming.
 *
 * Coordinate mapping: touch positions are mapped to normalized (0-1) page coordinates
 * using the page's render dimensions and offset within the view.
 */
@Composable
fun InlineAnnotationOverlay(
    pageIndex: Int,
    initialStrokes: List<AnnotationStroke>,
    pageRenderWidth: Float,
    pageRenderHeight: Float,
    pageOffsetX: Float,
    pageOffsetY: Float,
    onSave: (List<AnnotationStroke>) -> Unit,
    onCancel: () -> Unit
) {
    var strokes by remember(pageIndex) { mutableStateOf(initialStrokes) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    var toolMode by remember { mutableStateOf(ToolMode.PEN) }
    var selectedColor by remember { mutableStateOf(PenColors[0]) }
    var highlighterColor by remember { mutableStateOf(HighlighterColors[0]) }
    var strokeSize by remember { mutableStateOf(StrokeSize.MEDIUM) }

    val canMap = pageRenderWidth > 0f && pageRenderHeight > 0f

    Box(modifier = Modifier.fillMaxSize()) {

        // Transparent drawing canvas — consumes ALL pointer events via awaitEachGesture
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(toolMode, strokeSize, selectedColor, highlighterColor, pageIndex, canMap) {
                    if (!canMap) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()

                        val pathPoints = mutableListOf(down.position)

                        if (toolMode == ToolMode.ERASER) {
                            val nx = (down.position.x - pageOffsetX) / pageRenderWidth
                            val ny = (down.position.y - pageOffsetY) / pageRenderHeight
                            strokes = strokes.filterNot { stroke ->
                                stroke.points.any { p ->
                                    val dx = p.x - nx; val dy = p.y - ny
                                    dx * dx + dy * dy < 0.0012f
                                }
                            }
                        }

                        var event: androidx.compose.ui.input.pointer.PointerEvent
                        do {
                            event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                val pos = change.position
                                if (toolMode == ToolMode.ERASER) {
                                    val nx = (pos.x - pageOffsetX) / pageRenderWidth
                                    val ny = (pos.y - pageOffsetY) / pageRenderHeight
                                    strokes = strokes.filterNot { stroke ->
                                        stroke.points.any { p ->
                                            val dx = p.x - nx; val dy = p.y - ny
                                            dx * dx + dy * dy < 0.0012f
                                        }
                                    }
                                } else {
                                    pathPoints.add(pos)
                                    currentPath = pathPoints.toList()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Gesture ended — finalize stroke
                        if (toolMode != ToolMode.ERASER && pathPoints.size >= 2) {
                            val normalizedPoints = pathPoints.mapNotNull { offset ->
                                val nx = (offset.x - pageOffsetX) / pageRenderWidth
                                val ny = (offset.y - pageOffsetY) / pageRenderHeight
                                if (nx in 0f..1f && ny in 0f..1f) NormalizedPoint(nx, ny)
                                else null
                            }
                            if (normalizedPoints.size >= 2) {
                                val isHL = toolMode == ToolMode.HIGHLIGHTER
                                val activeColor = if (isHL) highlighterColor else selectedColor
                                strokes = strokes + AnnotationStroke(
                                    points = normalizedPoints,
                                    color = activeColor.toArgb(),
                                    strokeWidth = if (isHL) HIGHLIGHTER_WIDTH else strokeSize.width,
                                    isHighlighter = isHL
                                )
                            }
                        }
                        currentPath = emptyList()
                    }
                }
        ) {
            // Render committed strokes in screen coordinates
            strokes.forEach { stroke ->
                val path = Path()
                if (stroke.points.isNotEmpty()) {
                    val start = stroke.points[0]
                    path.moveTo(
                        pageOffsetX + start.x * pageRenderWidth,
                        pageOffsetY + start.y * pageRenderHeight
                    )
                    for (i in 1 until stroke.points.size) {
                        val p = stroke.points[i]
                        path.lineTo(
                            pageOffsetX + p.x * pageRenderWidth,
                            pageOffsetY + p.y * pageRenderHeight
                        )
                    }
                    val strokeColor = if (stroke.isHighlighter)
                        Color(stroke.color).copy(alpha = HIGHLIGHTER_ALPHA)
                    else Color(stroke.color)
                    val cap = if (stroke.isHighlighter) StrokeCap.Square else StrokeCap.Round
                    drawPath(
                        path = path,
                        color = strokeColor,
                        style = Stroke(width = stroke.strokeWidth, cap = cap, join = StrokeJoin.Round),
                        blendMode = if (stroke.isHighlighter) BlendMode.Multiply else BlendMode.SrcOver
                    )
                }
            }

            // Render current (in-progress) stroke
            if (currentPath.size >= 2) {
                val path = Path()
                path.moveTo(currentPath[0].x, currentPath[0].y)
                for (i in 1 until currentPath.size) {
                    path.lineTo(currentPath[i].x, currentPath[i].y)
                }
                val isHL = toolMode == ToolMode.HIGHLIGHTER
                val drawColor = if (isHL)
                    highlighterColor.copy(alpha = HIGHLIGHTER_ALPHA) else selectedColor
                val drawWidth = if (isHL) HIGHLIGHTER_WIDTH else strokeSize.width
                val cap = if (isHL) StrokeCap.Square else StrokeCap.Round
                drawPath(
                    path = path,
                    color = drawColor,
                    style = Stroke(width = drawWidth, cap = cap, join = StrokeJoin.Round),
                    blendMode = if (isHL) BlendMode.Multiply else BlendMode.SrcOver
                )
            }
        }

        // ---- Top action bar ----
        Surface(
            color = OverlayFloatingSurface,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("退出标注")
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "第 ${pageIndex + 1} 页",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OverlayIconGrey
                )

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = { onSave(strokes) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF43A047))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---- Bottom floating toolbar ----
        Surface(
            color = OverlayFloatingSurface,
            shape = RoundedCornerShape(50),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .shadow(16.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ToolButton(
                    icon = Icons.Default.Edit,
                    label = "画笔",
                    isSelected = toolMode == ToolMode.PEN,
                    indicatorColor = selectedColor,
                    onClick = { toolMode = ToolMode.PEN }
                )
                ToolButton(
                    icon = Icons.Default.BorderColor,
                    label = "荧光",
                    isSelected = toolMode == ToolMode.HIGHLIGHTER,
                    indicatorColor = highlighterColor.copy(alpha = 0.7f),
                    onClick = { toolMode = ToolMode.HIGHLIGHTER }
                )
                ToolButton(
                    icon = Icons.Outlined.Delete,
                    label = "橡皮",
                    isSelected = toolMode == ToolMode.ERASER,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    onClick = { toolMode = ToolMode.ERASER }
                )

                Spacer(modifier = Modifier.width(4.dp))
                VerticalDivider(modifier = Modifier.height(28.dp), color = Color.LightGray)
                Spacer(modifier = Modifier.width(4.dp))

                when (toolMode) {
                    ToolMode.PEN -> {
                        PenColors.forEach { color ->
                            val isSel = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (isSel) Modifier.border(2.5.dp, Color(0xFF2C2C2C), CircleShape)
                                        else Modifier.border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        VerticalDivider(modifier = Modifier.height(28.dp), color = Color.LightGray)
                        Spacer(modifier = Modifier.width(4.dp))
                        StrokeSize.entries.forEach { size ->
                            val isSel = strokeSize == size
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) selectedColor.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable { strokeSize = size },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(size.dotSize.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) selectedColor else OverlayIconGrey.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                    ToolMode.HIGHLIGHTER -> {
                        HighlighterColors.forEach { color ->
                            val isSel = highlighterColor == color
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(color.copy(alpha = 0.65f))
                                    .then(
                                        if (isSel) Modifier.border(2.5.dp, Color(0xFF2C2C2C), RoundedCornerShape(6.dp))
                                        else Modifier.border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    )
                                    .clickable { highlighterColor = color }
                            )
                        }
                    }
                    ToolMode.ERASER -> {
                        Text(
                            "滑动擦除标注",
                            style = MaterialTheme.typography.bodySmall,
                            color = OverlayIconGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
                VerticalDivider(modifier = Modifier.height(28.dp), color = Color.LightGray)
                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤销",
                        tint = if (strokes.isNotEmpty()) OverlayIconGrey else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
