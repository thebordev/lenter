package com.theboringdevelopers.lenter.feature.preview.vector

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.Color
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Парсер Android Vector Drawable XML файлов
 */
object VectorDrawableParser {

    /**
     * Парсит XML файл Vector Drawable
     *
     * @param inputStream поток данных XML файла
     * @return распарсенные данные векторного изображения
     * @throws IllegalArgumentException если файл не является vector drawable
     */
    fun parse(inputStream: InputStream): VectorDrawableData {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        doc.documentElement.normalize()

        val root = doc.documentElement

        require(root.nodeName == "vector") {
            "Not a vector drawable: root element is '${root.nodeName}', expected 'vector'"
        }

        val width = root.getAttribute("android:width").parseSize() ?: 24f
        val height = root.getAttribute("android:height").parseSize() ?: 24f
        val viewportWidth = root.getAttribute("android:viewportWidth").toFloatOrNull() ?: width
        val viewportHeight = root.getAttribute("android:viewportHeight").toFloatOrNull() ?: height
        val tint = root.getAttribute("android:tint").parseColor()

        val paths = mutableListOf<PathData>()
        extractPaths(root, paths)

        return VectorDrawableData(width, height, viewportWidth, viewportHeight, paths, tint)
    }

    private fun extractPaths(node: Node, paths: MutableList<PathData>) {
        if (node is Element && node.nodeName == "path") {
            val pathString = node.getAttribute("android:pathData")
            if (pathString.isNotEmpty()) {
                val fillColor = node.getAttribute("android:fillColor").parseColor()
                val strokeColor = node.getAttribute("android:strokeColor").parseColor()
                val strokeWidth = node.getAttribute("android:strokeWidth").toFloatOrNull() ?: 0f

                paths.add(PathData(pathString, fillColor, strokeColor, strokeWidth))
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            extractPaths(children.item(i), paths)
        }
    }

    private fun String.parseSize(): Float? {
        return this.replace("dp", "")
            .replace("px", "")
            .replace("dip", "")
            .toFloatOrNull()
    }

    private fun String.parseColor(): Color? {
        if (isEmpty() || this == "@null") return null

        return runCatching {
            when {
                startsWith("#") -> parseHexColor()
                startsWith("@color/") -> Color.BLACK
                else -> null
            }
        }.getOrNull()
    }

    private fun String.parseHexColor(): Color? {
        val hex = substring(1)
        return when (hex.length) {
            6 -> {
                // #RRGGBB
                Color(hex.toInt(16))
            }

            8 -> {
                // #AARRGGBB
                Color(
                    hex.substring(2, 4).toInt(16), // R
                    hex.substring(4, 6).toInt(16), // G
                    hex.substring(6, 8).toInt(16), // B
                    hex.substring(0, 2).toInt(16)  // A
                )
            }

            3 -> {
                // #RGB -> #RRGGBB
                val r = hex[0].toString().repeat(2).toInt(16)
                val g = hex[1].toString().repeat(2).toInt(16)
                val b = hex[2].toString().repeat(2).toInt(16)
                Color(r, g, b)
            }

            4 -> {
                // #ARGB -> #AARRGGBB
                val a = hex[0].toString().repeat(2).toInt(16)
                val r = hex[1].toString().repeat(2).toInt(16)
                val g = hex[2].toString().repeat(2).toInt(16)
                val b = hex[3].toString().repeat(2).toInt(16)
                Color(r, g, b, a)
            }

            else -> null
        }
    }
}
