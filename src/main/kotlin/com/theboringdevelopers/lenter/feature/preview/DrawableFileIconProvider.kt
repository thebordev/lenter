package com.theboringdevelopers.lenter.feature.preview

import com.intellij.ide.IconProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState
import javax.swing.Icon

/**
 * Провайдер иконок для drawable файлов в дереве проекта.
 *
 * Показывает миниатюры изображений вместо стандартных иконок файлов.
 * Поддерживает: PNG, JPG, JPEG, WebP, XML (Vector Drawable).
 */
class DrawableFileIconProvider : IconProvider() {

    private companion object {
        private const val ICON_SIZE = 16
        private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "xml")
    }

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (!PreviewSettingsState.getInstance().drawablePreviewInTreeEnabled) {
            return null
        }

        val psiFile = element as? PsiFile ?: return null
        val virtualFile = psiFile.virtualFile ?: return null

        val extension = virtualFile.extension?.lowercase() ?: return null
        if (extension !in SUPPORTED_EXTENSIONS) return null

        if (!ResourceLookup.isDrawableResource(virtualFile)) return null

        return element.project.service<DrawableIconCache>().getIcon(virtualFile, ICON_SIZE)
    }
}
