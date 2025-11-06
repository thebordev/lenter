package com.theboringdevelopers.lenter.feature.preview.editor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.theboringdevelopers.lenter.feature.preview.vector.VectorDrawableParser
import com.theboringdevelopers.lenter.feature.preview.vector.VectorDrawableRenderer
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/**
 * Компонент для отображения preview Vector Drawable.
 */
class VectorDrawablePreviewComponent(
    private val file: VirtualFile,
) : JBPanel<VectorDrawablePreviewComponent>() {

    private val imageLabel = JBLabel()
    private val infoLabel = JBLabel()
    private val errorLabel = JBLabel()

    private var currentImage: BufferedImage? = null

    init {
        layout = BorderLayout()
        background = JBColor.background()

        setupUI()
        loadPreview()
    }

    private fun setupUI() {
        val imagePanel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            background = createCheckerboardBackground()
            border = JBUI.Borders.empty(20)
        }

        imageLabel.horizontalAlignment = SwingConstants.CENTER
        imageLabel.verticalAlignment = SwingConstants.CENTER
        imagePanel.add(imageLabel, BorderLayout.CENTER)

        val scrollPane = JBScrollPane(imagePanel).apply {
            border = null
        }
        add(scrollPane, BorderLayout.CENTER)

        val infoPanel = JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            background = JBColor.background()
            border = JBUI.Borders.empty(8, 12)
        }

        infoLabel.font = infoLabel.font.deriveFont(Font.PLAIN, 11f)
        infoLabel.foreground = JBColor.GRAY
        infoPanel.add(infoLabel, BorderLayout.WEST)

        add(infoPanel, BorderLayout.SOUTH)

        errorLabel.apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            foreground = JBColor.RED
            isVisible = false
        }
        add(errorLabel, BorderLayout.NORTH)
    }

    private fun createCheckerboardBackground(): Color {
        return if (JBColor.isBright()) {
            JBColor(Color(245, 245, 245), Color(245, 245, 245))
        } else {
            JBColor(Color(60, 63, 65), Color(60, 63, 65))
        }
    }

    fun loadPreview() {
        SwingUtilities.invokeLater {
            try {
                errorLabel.isVisible = false

                val settings = PreviewSettingsState.getInstance()
                val previewSize = settings.vectorDrawablePreviewSize

                file.inputStream.use { inputStream ->
                    val vectorData = VectorDrawableParser.parse(inputStream)
                    val renderedImage = VectorDrawableRenderer.render(vectorData, previewSize)

                    currentImage = renderedImage
                    imageLabel.icon = ImageIcon(renderedImage)

                    val info = buildString {
                        append("Size: ${vectorData.width.toInt()}×${vectorData.height.toInt()} dp")
                        append(" | ")
                        append("Viewport: ${vectorData.viewportWidth.toInt()}×${vectorData.viewportHeight.toInt()}")
                        append(" | ")
                        append("Paths: ${vectorData.paths.size}")
                        append(" | ")
                        append("Preview: ${previewSize}×${previewSize} px")
                    }
                    infoLabel.text = info
                }
            } catch (e: Exception) {
                showError("Failed to load vector drawable: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        imageLabel.icon = null
        errorLabel.text = "<html><body style='padding: 20px; text-align: center;'>" +
                "<h3>Preview Error</h3>" +
                "<p>$message</p>" +
                "</body></html>"
        errorLabel.isVisible = true
        infoLabel.text = "Error"
    }
}
