package com.theboringdevelopers.lenter.feature.preview.vector

import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlin.math.min

/**
 * Рендерер Android Vector Drawable в растровое изображение.
 *
 * Поддерживает основные SVG path команды: M, L, H, V, C, Z.
 */
object VectorDrawableRenderer {

    /**
     * Рендерит векторные данные в изображение заданного размера.
     *
     * @param data данные векторного изображения
     * @param size размер результирующего изображения в пикселях
     * @return отрендеренное изображение
     */
    fun render(data: VectorDrawableData, size: Int): BufferedImage {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            val scaleX = size.toFloat() / data.viewportWidth
            val scaleY = size.toFloat() / data.viewportHeight
            val scale = min(scaleX, scaleY)

            g2d.scale(scale.toDouble(), scale.toDouble())

            val offsetX = (size / scale - data.viewportWidth) / 2
            val offsetY = (size / scale - data.viewportHeight) / 2
            g2d.translate(offsetX.toDouble(), offsetY.toDouble())

            renderPaths(data, g2d)
        } finally {
            g2d.dispose()
        }

        return image
    }

    private fun renderPaths(data: VectorDrawableData, g2d: java.awt.Graphics2D) {
        for (pathData in data.paths) {
            runCatching {
                val path = PathParser.parse(pathData.pathString)

                if (pathData.fillColor != null || data.tint != null) {
                    g2d.color = pathData.fillColor ?: data.tint
                    g2d.fill(path)
                }

                if (pathData.strokeColor != null && pathData.strokeWidth > 0) {
                    g2d.color = pathData.strokeColor
                    g2d.stroke = BasicStroke(
                        pathData.strokeWidth,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND
                    )
                    g2d.draw(path)
                }
            }
        }
    }
}

/**
 * Парсер SVG path команд
 */
private object PathParser {

    fun parse(pathString: String): Path2D.Float {
        val path = Path2D.Float()
        val tokens = tokenize(pathString)

        var i = 0
        var currentX = 0f
        var currentY = 0f
        var lastCommand = ' '

        while (i < tokens.size) {
            val token = tokens[i]

            when {
                token.isSingleLetterCommand() -> {
                    lastCommand = token[0]
                    i++
                }

                else -> {
                    i = processCommand(
                        command = lastCommand,
                        tokens = tokens,
                        startIndex = i,
                        path = path,
                        currentX = currentX,
                        currentY = currentY
                    ) { x, y ->
                        currentX = x
                        currentY = y
                    }
                }
            }
        }

        return path
    }

    private fun String.isSingleLetterCommand() = length == 1 && this[0].isLetter()

    private fun processCommand(
        command: Char,
        tokens: List<String>,
        startIndex: Int,
        path: Path2D.Float,
        currentX: Float,
        currentY: Float,
        updatePosition: (Float, Float) -> Unit
    ): Int {
        var newX = currentX
        var newY = currentY
        var nextIndex = startIndex

        when (command.uppercaseChar()) {
            'M' -> nextIndex = handleMoveTo(command, tokens, startIndex, path, currentX, currentY) { x, y ->
                newX = x
                newY = y
            }

            'L' -> nextIndex = handleLineTo(command, tokens, startIndex, path, currentX, currentY) { x, y ->
                newX = x
                newY = y
            }

            'H' -> nextIndex = handleHorizontalLine(command, tokens, startIndex, path, currentX, currentY) { x, _ ->
                newX = x
            }

            'V' -> nextIndex = handleVerticalLine(command, tokens, startIndex, path, currentX, currentY) { _, y ->
                newY = y
            }

            'C' -> nextIndex = handleCubicBezier(command, tokens, startIndex, path, currentX, currentY) { x, y ->
                newX = x
                newY = y
            }

            'Z' -> {
                path.closePath()
                nextIndex = startIndex + 1
            }

            else -> nextIndex = startIndex + 1 // Пропускаем неизвестные команды
        }

        updatePosition(newX, newY)
        return nextIndex
    }

    private fun handleMoveTo(
        command: Char,
        tokens: List<String>,
        index: Int,
        path: Path2D.Float,
        currentX: Float,
        currentY: Float,
        updatePosition: (Float, Float) -> Unit
    ): Int {
        val x = tokens.getFloatOrZero(index)
        val y = tokens.getFloatOrZero(index + 1)

        if (command.isUpperCase()) {
            path.moveTo(x, y)
            updatePosition(x, y)
        } else {
            path.moveTo(currentX + x, currentY + y)
            updatePosition(currentX + x, currentY + y)
        }

        return index + 2
    }

    private fun handleLineTo(
        command: Char,
        tokens: List<String>,
        index: Int,
        path: Path2D.Float,
        currentX: Float,
        currentY: Float,
        updatePosition: (Float, Float) -> Unit
    ): Int {
        val x = tokens.getFloatOrZero(index)
        val y = tokens.getFloatOrZero(index + 1)

        if (command.isUpperCase()) {
            path.lineTo(x, y)
            updatePosition(x, y)
        } else {
            path.lineTo(currentX + x, currentY + y)
            updatePosition(currentX + x, currentY + y)
        }

        return index + 2
    }

    private fun handleHorizontalLine(
        command: Char,
        tokens: List<String>,
        index: Int,
        path: Path2D.Float,
        currentX: Float,
        currentY: Float,
        updatePosition: (Float, Float) -> Unit
    ): Int {
        val x = tokens.getFloatOrZero(index)

        if (command.isUpperCase()) {
            path.lineTo(x, currentY)
            updatePosition(x, currentY)
        } else {
            path.lineTo(currentX + x, currentY)
            updatePosition(currentX + x, currentY)
        }

        return index + 1
    }

    private fun handleVerticalLine(
        command: Char,
        tokens: List<String>,
        index: Int,
        path: Path2D.Float,
        currentX: Float,
        currentY: Float,
        updatePosition: (Float, Float) -> Unit
    ): Int {
        val y = tokens.getFloatOrZero(index)

        if (command.isUpperCase()) {
            path.lineTo(currentX, y)
            updatePosition(currentX, y)
        } else {
            path.lineTo(currentX, currentY + y)
            updatePosition(currentX, currentY + y)
        }

        return index + 1
    }

    private fun handleCubicBezier(
        command: Char,
        tokens: List<String>,
        index: Int,
        path: Path2D.Float,
        currentX: Float,
        currentY: Float,
        updatePosition: (Float, Float) -> Unit
    ): Int {
        val x1 = tokens.getFloatOrZero(index)
        val y1 = tokens.getFloatOrZero(index + 1)
        val x2 = tokens.getFloatOrZero(index + 2)
        val y2 = tokens.getFloatOrZero(index + 3)
        val x = tokens.getFloatOrZero(index + 4)
        val y = tokens.getFloatOrZero(index + 5)

        if (command.isUpperCase()) {
            path.curveTo(x1, y1, x2, y2, x, y)
            updatePosition(x, y)
        } else {
            path.curveTo(
                currentX + x1, currentY + y1,
                currentX + x2, currentY + y2,
                currentX + x, currentY + y
            )
            updatePosition(currentX + x, currentY + y)
        }

        return index + 6
    }

    private fun tokenize(pathString: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()

        for (char in pathString) {
            when {
                char.isWhitespace() || char == ',' -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }

                char.isLetter() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                    tokens.add(char.toString())
                }

                char == '-' && current.isNotEmpty() -> {
                    tokens.add(current.toString())
                    current = StringBuilder(char.toString())
                }

                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    private fun List<String>.getFloatOrZero(index: Int): Float {
        return getOrNull(index)?.toFloatOrNull() ?: 0f
    }
}
