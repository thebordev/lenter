package com.theboringdevelopers.lenter.feature.colors

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState
import org.jetbrains.kotlin.psi.*
import java.awt.Color
import java.util.Locale

class ComposeColorProvider : ElementColorProvider {

    override fun getColorFrom(element: PsiElement): Color? {
        if (!PreviewSettingsState.getInstance().composeColorPreviewEnabled) {
            return null
        }

        getColorFromHexLiteral(element)?.let { return it }

        getColorFromPredefinedConstant(element)?.let { return it }

        return null
    }

    override fun setColorTo(element: PsiElement, color: Color) {
        if (!PreviewSettingsState.getInstance().composeColorPreviewEnabled) {
            return
        }

        if (element is KtConstantExpression && element.text.matches(HEX_LITERAL_REGEX)) {
            setHexLiteralColor(element, color)
            return
        }

        if (element is KtNameReferenceExpression) {
            replacePredefinedColorWithHex(element, color)
            return
        }
    }

    /**
     * Получить цвет из hex-литерала (0xFFFF0000)
     */
    private fun getColorFromHexLiteral(element: PsiElement): Color? {
        if (element !is KtConstantExpression) return null

        val text = element.text
        if (!text.matches(HEX_LITERAL_REGEX)) return null

        val valueArg = element.parent as? KtValueArgument ?: return null
        val argList = valueArg.parent as? KtValueArgumentList ?: return null
        val callExpr = argList.parent as? KtCallExpression ?: return null

        val calleeName = callExpr.calleeExpression?.text ?: return null
        if (!calleeName.endsWith("Color")) return null

        return text.parseComposeHexColor()
    }

    /**
     * Получить цвет из предопределенной константы (Color.Red, Red и т.д.)
     */
    private fun getColorFromPredefinedConstant(element: PsiElement): Color? {
        val colorName = when (element) {
            is KtNameReferenceExpression -> {
                val parent = element.parent
                if (parent is KtDotQualifiedExpression) {
                    val receiver = parent.receiverExpression
                    if (receiver.text == "Color") {
                        element.getReferencedName()
                    } else {
                        null
                    }
                } else {
                    element.getReferencedName()
                }
            }

            is KtSimpleNameExpression -> {
                val parent = element.parent
                if (parent is KtDotQualifiedExpression && element == parent.receiverExpression) {
                    null
                } else {
                    element.getReferencedName()
                }
            }

            else -> null
        } ?: return null

        return PREDEFINED_COMPOSE_COLORS[colorName]
    }

    /**
     * Изменить hex-литерал
     */
    private fun setHexLiteralColor(element: KtConstantExpression, color: Color) {
        if (!element.isValid) return

        val project = element.project
        val containingFile = element.containingFile
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val document = psiDocumentManager.getDocument(containingFile) ?: return

        val textRange = element.textRange
        val currentText = element.text
        val suffix = currentText.extractNumericSuffix()

        val newColorHex = color.toComposeHexLiteral() + suffix

        if (currentText == newColorHex) return

        WriteCommandAction.runWriteCommandAction(project, "Change Compose Color", null, {
            document.replaceString(textRange.startOffset, textRange.endOffset, newColorHex)
            psiDocumentManager.commitDocument(document)
        })
    }

    /**
     * Заменить предопределенную константу на hex-литерал
     */
    private fun replacePredefinedColorWithHex(element: KtNameReferenceExpression, color: Color) {
        if (!element.isValid) return

        val project = element.project
        val containingFile = element.containingFile
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        val document = psiDocumentManager.getDocument(containingFile) ?: return

        val (startOffset, endOffset) = when (val parent = element.parent) {
            is KtDotQualifiedExpression -> {
                parent.textRange.startOffset to parent.textRange.endOffset
            }

            else -> {
                element.textRange.startOffset to element.textRange.endOffset
            }
        }

        val newColorCode = "Color(${color.toComposeHexLiteral()})"

        WriteCommandAction.runWriteCommandAction(project, "Change Compose Color", null, {
            document.replaceString(startOffset, endOffset, newColorCode)
            psiDocumentManager.commitDocument(document)
        })
    }

    companion object {
        /**
         * Предопределенные цвета Compose
         */
        private val PREDEFINED_COMPOSE_COLORS = mapOf(
            "Red" to Color(0xFF, 0x00, 0x00),
            "Green" to Color(0x00, 0xFF, 0x00),
            "Blue" to Color(0x00, 0x00, 0xFF),
            "Yellow" to Color(0xFF, 0xFF, 0x00),
            "Cyan" to Color(0x00, 0xFF, 0xFF),
            "Magenta" to Color(0xFF, 0x00, 0xFF),
            "White" to Color(0xFF, 0xFF, 0xFF),
            "Black" to Color(0x00, 0x00, 0x00),
            "Gray" to Color(0x88, 0x88, 0x88),
            "LightGray" to Color(0xCC, 0xCC, 0xCC),
            "DarkGray" to Color(0x44, 0x44, 0x44),
            "Transparent" to Color(0x00, 0x00, 0x00, 0x00),
            "Unspecified" to Color(0x00, 0x00, 0x00, 0x00)
        )
    }
}

/**
 * Парсит hex строку в Color
 */
private fun String.parseComposeHexColor(): Color? {
    val match = HEX_LITERAL_REGEX.matchEntire(this) ?: return null
    val valuePart = match.groupValues[1].replace("_", "")
    if (valuePart.isEmpty()) return null

    val parsed = valuePart.toLongOrNull(16) ?: return null

    val argb = when (valuePart.length) {
        8 -> parsed.toInt()
        6 -> (0xFF shl 24) or parsed.toInt()
        4 -> {
            val a = ((parsed shr 12) and 0xF).toInt()
            val r = ((parsed shr 8) and 0xF).toInt()
            val g = ((parsed shr 4) and 0xF).toInt()
            val b = (parsed and 0xF).toInt()
            ((a * 17) shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
        }

        3 -> {
            val r = ((parsed shr 8) and 0xF).toInt()
            val g = ((parsed shr 4) and 0xF).toInt()
            val b = (parsed and 0xF).toInt()
            (0xFF shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
        }

        else -> return null
    }

    return Color(argb, true)
}

/**
 * Извлекает суффикс (u, U, l, L)
 */
private fun String.extractNumericSuffix(): String {
    val match = HEX_LITERAL_REGEX.matchEntire(this)
    return match?.groupValues?.getOrNull(2) ?: ""
}

/**
 * Конвертирует Color в hex строку
 */
private fun Color.toComposeHexLiteral(): String {
    val argb = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    return "0x%08X".format(Locale.US, argb)
}

private val HEX_LITERAL_REGEX = Regex("^0[xX]([0-9a-fA-F_]+)([uUlL]*)$")
