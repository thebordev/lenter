package com.theboringdevelopers.lenter.feature.preview

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState
import org.jetbrains.kotlin.psi.*
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPanel

/**
 * Провайдер inline hints для preview drawable ресурсов в коде.
 *
 * Показывает миниатюры рядом с вызовами resource функций:
 * - painterResource() - Android & Compose Multiplatform
 * - vectorResource() - специально для векторных изображений
 *
 * Поддерживает:
 * - Android: R.drawable.*
 * - Compose Multiplatform: Res.drawable.*
 */
@Suppress("UnstableApiUsage")
class DrawableInlinePreviewProvider : InlayHintsProvider<NoSettings> {

    private companion object {
        private val KEY = SettingsKey<NoSettings>("lenter.drawable.inline.preview")
        private const val ICON_SIZE = 16

        private val SUPPORTED_RESOURCE_FUNCTIONS = setOf("painterResource", "vectorResource")

        private val ANDROID_DRAWABLE_REGEX = Regex("""R\.drawable\.([A-Za-z0-9_]+)""")
        private val CMP_DRAWABLE_REGEX = Regex("""Res\.drawable\.([A-Za-z0-9_]+)""")
    }

    override val key: SettingsKey<NoSettings> = KEY
    override val name: String = "Drawable inline preview in code"
    override val group: InlayGroup = InlayGroup.TYPES_GROUP
    override val previewText: String = """
        @Composable
        fun Demo() {
            Icon(painterResource(Res.drawable.ic_car), null)
            Icon(vectorResource(Res.drawable.ic_star), null)
        }
    """.trimIndent()

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (!PreviewSettingsState.getInstance().drawablePreviewInCodeEnabled) {
            return null
        }

        if (file !is KtFile) return null

        return DrawableHintsCollector(editor)
    }

    private inner class DrawableHintsCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (element !is KtCallExpression) return true

            val calleeName = element.calleeExpression?.text ?: return true
            if (calleeName !in SUPPORTED_RESOURCE_FUNCTIONS) return true

            val arg = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return true

            val drawableName = arg.extractDrawableName() ?: return true

            val project = element.project
            val virtualFile = ResourceLookup.findDrawableFile(project, drawableName) ?: return true

            val icon = project.service<DrawableIconCache>().getIcon(virtualFile, ICON_SIZE)

            val presentation = createIconPresentation(icon, calleeName)

            val offset = element.textRange.endOffset
            sink.addInlineElement(offset, true, presentation, false)

            return true
        }

        private fun createIconPresentation(icon: Icon?, resourceType: String): InlayPresentation {
            val iconPresentation = if (icon != null) {
                factory.icon(icon)
            } else {
                factory.icon(createPlaceholderIcon(resourceType))
            }

            return factory.seq(
                factory.textSpacePlaceholder(1, true),
                iconPresentation
            )
        }

        private fun createPlaceholderIcon(resourceType: String): Icon {
            val image = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()

            try {
                val (bgColor, borderColor) = when (resourceType) {
                    "vectorResource" -> Color(220, 200, 255) to Color(150, 100, 200) // Фиолетовый
                    else -> Color(200, 200, 200) to Color(150, 150, 150) // Серый
                }

                g.color = bgColor
                g.fillRect(0, 0, ICON_SIZE, ICON_SIZE)
                g.color = borderColor
                g.drawRect(0, 0, ICON_SIZE - 1, ICON_SIZE - 1)
                g.drawLine(0, 0, ICON_SIZE - 1, ICON_SIZE - 1)
                g.drawLine(0, ICON_SIZE - 1, ICON_SIZE - 1, 0)
            } finally {
                g.dispose()
            }

            return ImageIcon(image)
        }
    }

    private fun KtExpression.extractDrawableName(): String? {
        val text = this.text

        ANDROID_DRAWABLE_REGEX.find(text)?.let {
            return it.groupValues[1]
        }

        CMP_DRAWABLE_REGEX.find(text)?.let {
            return it.groupValues[1]
        }

        if (this is KtStringTemplateExpression && !this.hasInterpolation()) {
            val value = this.entries.firstOrNull()?.text
            if (!value.isNullOrBlank()) return value
        }

        if (this is KtDotQualifiedExpression) {
            val selector = this.selectorExpression
            if (selector is KtNameReferenceExpression) {
                return selector.getReferencedName()
            }
        }

        return null
    }
}
