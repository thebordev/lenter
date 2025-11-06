package com.theboringdevelopers.lenter.feature.preview.string

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationListener
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState
import org.jetbrains.kotlin.psi.*
import java.awt.*
import javax.swing.JPanel

/**
 * Провайдер inline hints для preview строковых ресурсов.
 */
@Suppress("UnstableApiUsage")
class StringResourceInlayProvider : InlayHintsProvider<NoSettings> {

    private companion object {
        private val KEY = SettingsKey<NoSettings>("lenter.string.resource.preview")

        private val ANDROID_STRING_REGEX = Regex("""R\.string\.([A-Za-z0-9_]+)""")
        private val CMP_STRING_REGEX = Regex("""Res\.string\.([A-Za-z0-9_]+)""")

        private const val MAX_PREVIEW_LENGTH = 60
    }

    override val key: SettingsKey<NoSettings> = KEY
    override val name: String = "String resource preview in code"
    override val group: InlayGroup = InlayGroup.VALUES_GROUP
    override val previewText: String = """
        @Composable
        fun Demo() {
            Text(stringResource(Res.string.app_name))
            Text(stringResource(R.string.welcome_message))
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
        val previewSettings = PreviewSettingsState.getInstance()
        if (!previewSettings.stringResourcePreviewEnabled) {
            return null
        }

        if (file !is KtFile) return null

        return StringResourceHintsCollector(editor)
    }

    private inner class StringResourceHintsCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (element !is KtCallExpression) return true

            val calleeName = element.calleeExpression?.text
            if (calleeName != "stringResource") return true

            val arg = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return true
            val stringName = arg.extractStringName() ?: return true

            val project = element.project
            val cache = project.service<StringResourceCache>()
            val bundle = cache.getStringBundle(project, stringName) ?: return true

            val settings = PreviewSettingsState.getInstance()
            val preferredLanguage = settings.stringResourcePreferredLanguage
            val stringValue = bundle.getValue(preferredLanguage) ?: return true
            val actualLanguage = bundle.getActualLanguage(preferredLanguage)

            val presentation = createStringPresentation(
                editor,
                stringValue,
                actualLanguage,
                preferredLanguage
            )

            val offset = element.textRange.endOffset
            sink.addInlineElement(offset, true, presentation, false)

            return true
        }

        private fun createStringPresentation(
            editor: Editor,
            value: String,
            actualLanguage: String,
            preferredLanguage: String
        ): InlayPresentation {
            val displayValue = if (value.length > MAX_PREVIEW_LENGTH) {
                value.take(MAX_PREVIEW_LENGTH) + "..."
            } else {
                value
            }

            val escaped = displayValue
                .replace("\n", "\\n")
                .replace("\t", "\\t")

            val languageTag = if (actualLanguage != preferredLanguage && preferredLanguage != "default") {
                " [$actualLanguage]"
            } else {
                ""
            }

            val fullText = "$escaped$languageTag"

            return factory.seq(
                factory.textSpacePlaceholder(2, true),
                StringPreviewPresentation(fullText, editor)
            )
        }
    }

    private fun KtExpression.extractStringName(): String? {
        val text = this.text

        ANDROID_STRING_REGEX.find(text)?.let {
            return it.groupValues[1]
        }

        CMP_STRING_REGEX.find(text)?.let {
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

    /**
     * Кастомная презентация с адаптивными цветами для светлой и темной темы.
     */
    private class StringPreviewPresentation(
        private val text: String,
        private val editor: Editor
    ) : InlayPresentation {

        private val padding = 4
        private val arcSize = 6
        private val listeners = mutableListOf<PresentationListener>()

        override val width: Int
            get() = calculateWidth() + padding * 2

        override val height: Int
            get() = editor.lineHeight

        override fun paint(g: Graphics2D, attributes: TextAttributes) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val colors = getThemeColors()

                g2.color = colors.background
                g2.fillRoundRect(0, 1, width - 1, height - 3, arcSize, arcSize)

                g2.color = colors.border
                g2.drawRoundRect(0, 1, width - 1, height - 3, arcSize, arcSize)

                g2.color = colors.foreground
                g2.font = getFont()

                val fontMetrics = g2.fontMetrics
                val textY = (height + fontMetrics.ascent - fontMetrics.descent) / 2

                g2.drawString(text, padding, textY)
            } finally {
                g2.dispose()
            }
        }

        override fun addListener(listener: PresentationListener) {
            listeners.add(listener)
        }

        override fun removeListener(listener: PresentationListener) {
            listeners.remove(listener)
        }

        override fun fireContentChanged(area: Rectangle) {
            listeners.forEach { it.contentChanged(area) }
        }

        override fun fireSizeChanged(previous: Dimension, current: Dimension) {
            listeners.forEach { it.sizeChanged(previous, current) }
        }

        override fun toString(): String = text

        private fun calculateWidth(): Int {
            val fontMetrics = editor.component.getFontMetrics(getFont())
            return fontMetrics.stringWidth(text)
        }

        private fun getFont(): Font {
            val editorFont = editor.colorsScheme.getFont(EditorFontType.PLAIN)
            return editorFont.deriveFont(editorFont.size2D - 1f)
        }

        private fun getThemeColors(): ThemeColors {
            val isDark = EditorColorsManager.getInstance().isDarkEditor

            return if (isDark) {
                // Темная тема
                ThemeColors(
                    background = JBColor(Color(60, 63, 65), Color(60, 63, 65)),
                    foreground = JBColor(Color(169, 183, 198), Color(169, 183, 198)),
                    border = JBColor(Color(80, 83, 85), Color(80, 83, 85))
                )
            } else {
                // Светлая тема
                ThemeColors(
                    background = JBColor(Color(245, 245, 245), Color(245, 245, 245)),
                    foreground = JBColor(Color(96, 96, 96), Color(96, 96, 96)),
                    border = JBColor(Color(200, 200, 200), Color(200, 200, 200))
                )
            }
        }

        private data class ThemeColors(
            val background: Color,
            val foreground: Color,
            val border: Color
        )
    }
}
