package com.theboringdevelopers.lenter.feature.preview

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ImageUtil
import com.theboringdevelopers.lenter.feature.preview.vector.VectorDrawableParser
import com.theboringdevelopers.lenter.feature.preview.vector.VectorDrawableRenderer
import java.awt.Image
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Кэш иконок drawable ресурсов с поддержкой автоматической инвалидации.
 *
 * Использует [SoftReference] для экономии памяти и проверку modification stamp
 * для автоматического обновления при изменении файлов.
 */
@Service(Service.Level.PROJECT)
class DrawableIconCache {

    private data class CacheEntry(
        val modificationStamp: Long,
        val iconRef: SoftReference<Icon?>
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Получает иконку для drawable файла с учетом кэша.
     *
     * @param file файл drawable ресурса
     * @param size желаемый размер иконки в пикселях
     * @return иконка или null при ошибке загрузки
     */
    fun getIcon(file: VirtualFile, size: Int): Icon? {
        val cacheKey = "${file.path}@$size"
        val currentStamp = file.modificationStamp

        cache[cacheKey]?.let { entry ->
            if (entry.modificationStamp == currentStamp) {
                entry.iconRef.get()?.let { return it }
            }
        }

        val icon = loadIcon(file, size)

        if (icon != null) {
            cache[cacheKey] = CacheEntry(currentStamp, SoftReference(icon))
        }

        return icon
    }

    private fun loadIcon(file: VirtualFile, size: Int): Icon? {
        return try {
            when (file.extension?.lowercase()) {
                "xml" -> loadVectorDrawable(file, size)
                "png", "jpg", "jpeg", "webp" -> loadRasterImage(file, size)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadRasterImage(file: VirtualFile, size: Int): Icon? {
        return try {
            file.inputStream.use { inputStream ->
                val image = ImageIO.read(inputStream) ?: return null
                val scaledImage = scaleAndCropToSquare(image, size)
                ImageIcon(scaledImage)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadVectorDrawable(file: VirtualFile, size: Int): Icon? {
        return try {
            file.inputStream.use { inputStream ->
                val vectorData = VectorDrawableParser.parse(inputStream)
                val image = VectorDrawableRenderer.render(vectorData, size)
                ImageIcon(image)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleAndCropToSquare(image: Image, targetSize: Int): Image {
        val width = image.getWidth(null)
        val height = image.getHeight(null)

        if (width <= 0 || height <= 0) return image

        val minDimension = minOf(width, height)
        val scale = targetSize.toDouble() / minDimension

        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)

        val scaledImage = ImageUtil.scaleImage(image, scaledWidth, scaledHeight)

        if (scaledWidth == scaledHeight) return scaledImage

        val bufferedImage = scaledImage.toBufferedImage(scaledWidth, scaledHeight)

        val x = if (scaledWidth > targetSize) (scaledWidth - targetSize) / 2 else 0
        val y = if (scaledHeight > targetSize) (scaledHeight - targetSize) / 2 else 0

        return bufferedImage.getSubimage(
            x, y,
            minOf(targetSize, scaledWidth),
            minOf(targetSize, scaledHeight)
        )
    }

    private fun Image.toBufferedImage(width: Int, height: Int): BufferedImage {
        if (this is BufferedImage) return this

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = bufferedImage.createGraphics()
        try {
            g.drawImage(this, 0, 0, null)
        } finally {
            g.dispose()
        }
        return bufferedImage
    }
}
