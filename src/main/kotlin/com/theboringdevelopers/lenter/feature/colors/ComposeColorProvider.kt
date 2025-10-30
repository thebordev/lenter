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

    override fun setColorTo(element: PsiElement, color: Color) {
        if (!PreviewSettingsState.getInstance().composeColorPreviewEnabled) {
            return
        }

        if (element !is KtConstantExpression) return
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
