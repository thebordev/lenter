package com.theboringdevelopers.lenter.feature.preview.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
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
 * Панель с preview и toolbar для управления.
 */
class VectorDrawablePreviewPanel(
    private val file: VirtualFile
) : Disposable {

    private val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
    private val imageLabel = JBLabel()
    private val infoLabel = JBLabel()
    private val errorPanel = createErrorPanel()
    private val scrollPane: JBScrollPane

    private var currentImage: BufferedImage? = null
    private var currentZoomLevel: Int = 100

    init {
        mainPanel.background = JBColor.background()

        val toolbar = createToolbar()
        mainPanel.add(toolbar.component, BorderLayout.NORTH)

        val imagePanel = createImagePanel()
        scrollPane = JBScrollPane(imagePanel).apply {
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        val infoPanel = createInfoPanel()
        mainPanel.add(infoPanel, BorderLayout.SOUTH)

        loadPreview()
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            add(ZoomInAction())
            add(ZoomOutAction())
            add(ZoomResetAction())
            add(ZoomFitAction())
            addSeparator()
            add(ExportAction())
        }

        return ActionManager.getInstance().createActionToolbar(
            "VectorDrawablePreview",
            actionGroup,
            true
        ).apply {
            targetComponent = mainPanel
        }
    }

    private fun createImagePanel(): JPanel {
        return object : JBPanel<JBPanel<*>>() {
            init {
                layout = GridBagLayout()
                background = createCheckerboardBackground()
                border = JBUI.Borders.empty(20)

                imageLabel.horizontalAlignment = SwingConstants.CENTER
                imageLabel.verticalAlignment = SwingConstants.CENTER

                add(imageLabel, GridBagConstraints().apply {
                    gridx = 0
                    gridy = 0
                    anchor = GridBagConstraints.CENTER
                })
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                drawCheckerboard(g as Graphics2D, bounds)
            }
        }
    }

    private fun createInfoPanel(): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            background = JBColor.background()
            border = JBUI.Borders.empty(8, 12)

            infoLabel.font = infoLabel.font.deriveFont(Font.PLAIN, 11f)
            infoLabel.foreground = JBColor.GRAY
            add(infoLabel, BorderLayout.CENTER)
        }
    }

    private fun createErrorPanel(): JPanel {
        return JBPanel<JBPanel<*>>().apply {
            layout = BorderLayout()
            background = JBColor.background()
            border = JBUI.Borders.empty(20)
            isVisible = false

            val errorLabel = JBLabel().apply {
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                foreground = JBColor.RED
            }
            add(errorLabel, BorderLayout.CENTER)
        }
    }

    private fun createCheckerboardBackground(): Color {
        return if (JBColor.isBright()) {
            JBColor(Color(250, 250, 250), Color(250, 250, 250))
        } else {
            JBColor(Color(50, 50, 50), Color(50, 50, 50))
        }
    }

    private fun drawCheckerboard(g: Graphics2D, bounds: Rectangle) {
        val checkSize = 8
        val color1 = if (JBColor.isBright()) Color(230, 230, 230) else Color(60, 60, 60)
        val color2 = if (JBColor.isBright()) Color(250, 250, 250) else Color(50, 50, 50)

        var y = 0
        while (y < bounds.height) {
            var x = 0
            while (x < bounds.width) {
                val isEven = ((x / checkSize) + (y / checkSize)) % 2 == 0
                g.color = if (isEven) color1 else color2
                g.fillRect(x, y, checkSize, checkSize)
                x += checkSize
            }
            y += checkSize
        }
    }

    fun refresh() {
        SwingUtilities.invokeLater { loadPreview() }
    }

    private fun loadPreview() {
        try {
            errorPanel.isVisible = false

            val settings = PreviewSettingsState.getInstance()
            val baseSize = settings.vectorDrawablePreviewSize
            val actualSize = (baseSize * currentZoomLevel / 100.0).toInt().coerceIn(16, 2048)

            file.inputStream.use { inputStream ->
                val vectorData = VectorDrawableParser.parse(inputStream)
                val renderedImage = VectorDrawableRenderer.render(vectorData, actualSize)

                currentImage = renderedImage
                imageLabel.icon = ImageIcon(renderedImage)

                updateInfo(
                    vectorData.width, vectorData.height, vectorData.viewportWidth,
                    vectorData.viewportHeight, vectorData.paths.size, actualSize
                )
            }
        } catch (e: Exception) {
            showError("Failed to render vector: ${e.message}")
        }
    }

    private fun updateInfo(
        width: Float, height: Float, vpWidth: Float, vpHeight: Float,
        pathCount: Int, previewSize: Int
    ) {
        infoLabel.text = buildString {
            append("Size: ${width.toInt()}×${height.toInt()} dp")
            append(" | ")
            append("Viewport: ${vpWidth.toInt()}×${vpHeight.toInt()}")
            append(" | ")
            append("Paths: $pathCount")
            append(" | ")
            append("Preview: ${previewSize}×${previewSize} px")
            append(" | ")
            append("Zoom: $currentZoomLevel%")
        }
    }

    private fun showError(message: String) {
        imageLabel.icon = null
        infoLabel.text = "Error loading preview"

        val errorLabel = (errorPanel.components.firstOrNull() as? JBLabel)
        errorLabel?.text = "<html><body style='padding: 20px; text-align: center;'>" +
                "<h3>Preview Error</h3>" +
                "<p>$message</p>" +
                "</body></html>"
        errorPanel.isVisible = true
    }

    fun zoomIn() {
        currentZoomLevel = (currentZoomLevel * 1.25).toInt().coerceAtMost(400)
        loadPreview()
    }

    fun zoomOut() {
        currentZoomLevel = (currentZoomLevel * 0.8).toInt().coerceAtLeast(25)
        loadPreview()
    }

    fun zoomReset() {
        currentZoomLevel = 100
        loadPreview()
    }

    fun zoomFit() {
        currentImage?.let { img ->
            val viewportWidth = scrollPane.viewport.width - 40
            val viewportHeight = scrollPane.viewport.height - 40

            val scaleW = viewportWidth.toDouble() / img.width
            val scaleH = viewportHeight.toDouble() / img.height
            val scale = minOf(scaleW, scaleH, 1.0)

            currentZoomLevel = (scale * 100).toInt().coerceIn(25, 400)
            loadPreview()
        }
    }

    fun exportImage() {
        JOptionPane.showMessageDialog(
            mainPanel,
            "Export functionality will be implemented soon",
            "Export",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    val component: JComponent get() = mainPanel

    override fun dispose() {}

    private inner class RefreshAction : AnAction(
        "Refresh",
        "Reload preview",
        AllIcons.Actions.Refresh
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            refresh()
        }
    }

    private inner class ZoomInAction : AnAction(
        "Zoom In",
        "Increase preview size (Ctrl +)",
        AllIcons.Graph.ZoomIn
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            zoomIn()
        }
    }

    private inner class ZoomOutAction : AnAction(
        "Zoom Out",
        "Decrease preview size (Ctrl -)",
        AllIcons.Graph.ZoomOut
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            zoomOut()
        }
    }

    private inner class ZoomResetAction : AnAction(
        "100%",
        "Reset zoom to 100% (Ctrl 0)",
        AllIcons.Graph.ActualZoom
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            zoomReset()
        }
    }

    private inner class ZoomFitAction : AnAction(
        "Fit",
        "Fit to window",
        AllIcons.Actions.Preview
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            zoomFit()
        }
    }

    private inner class ExportAction : AnAction(
        "Export",
        "Export as PNG",
        AllIcons.ToolbarDecorator.Export
    ), DumbAware {
        override fun actionPerformed(e: AnActionEvent) {
            exportImage()
        }
    }
}
