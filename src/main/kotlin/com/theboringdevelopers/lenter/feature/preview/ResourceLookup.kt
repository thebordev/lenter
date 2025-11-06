package com.theboringdevelopers.lenter.feature.preview

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Утилита для поиска drawable ресурсов в проекте.
 *
 * Поддерживает:
 * - Android: res/drawable*
 * - Compose Multiplatform: composeResources/drawable*
 */
object ResourceLookup {

    private val SUPPORTED_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "svg", "xml")

    /**
     * Ищет файл drawable ресурса по имени.
     *
     * @param project проект для поиска
     * @param name имя ресурса без расширения
     * @return найденный файл или null
     */
    fun findDrawableFile(project: Project, name: String): VirtualFile? {
        val scope = GlobalSearchScope.projectScope(project)
        val candidates = buildList {
            SUPPORTED_EXTENSIONS.forEach { ext ->
                val fileName = "$name.$ext"
                addAll(FilenameIndex.getVirtualFilesByName(fileName, scope))
            }
        }

        if (candidates.isEmpty()) return null

        return candidates.sortedWith(DRAWABLE_PRIORITY_COMPARATOR).firstOrNull()
    }

    /**
     * Проверяет, является ли файл drawable ресурсом.
     */
    fun isDrawableResource(file: VirtualFile): Boolean {
        return file.path.contains("/drawable") && (
                file.path.contains("/res/drawable") ||
                        file.path.contains("/composeResources/drawable")
                )
    }

    private val DRAWABLE_PRIORITY_COMPARATOR = compareByDescending<VirtualFile> {
        it.path.contains("/composeResources/drawable")
    }.thenByDescending {
        it.path.contains("/res/drawable")
    }.thenBy {
        it.path.length
    }
}
