package com.theboringdevelopers.lenter.feature.preview.vector

import java.awt.Color

/**
 * Модель данных Android Vector Drawable
 */
data class VectorDrawableData(
    val width: Float,
    val height: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val paths: List<PathData>,
    val tint: Color?
)

/**
 * Данные одного path элемента
 */
data class PathData(
    val pathString: String,
    val fillColor: Color?,
    val strokeColor: Color?,
    val strokeWidth: Float
)
