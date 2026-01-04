package com.example.paperlessmeeting.ui.screens.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// 归一化的点 (0.0 ~ 1.0)，相对于页面宽高的比例
data class NormalizedPoint(val x: Float, val y: Float)

data class AnnotationStroke(
    val points: List<NormalizedPoint>,
    val color: Int, // Android Int Color
    val strokeWidth: Float = 5f
)

// 一个页面上的所有笔记
data class PageAnnotations(
    val pageIndex: Int,
    val strokes: List<AnnotationStroke> = emptyList()
)

// Helper for Legacy compatibility if needed, or just new structure
data class DocumentAnnotations(
    val fileName: String,
    val lines: List<AnnotationLine> // Keeping this for now if migration is needed, but we will move to PageAnnotations
)

// Data class for legacy support or transitional logic
data class AnnotationLine(
    val pageIndex: Int,
    val points: List<PointFCompat>,
    val color: Int,
    val strokeWidth: Float
)

data class PointFCompat(
    val x: Float,
    val y: Float
)
