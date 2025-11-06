package com.theboringdevelopers.lenter.feature.preview.string

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Утилита для поиска строковых ресурсов в проекте.
 */
object StringResourceLookup {

    /**
     * Находит все strings.xml файлы в проекте.
     */
    fun findStringFiles(project: Project): List<VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        return FilenameIndex.getVirtualFilesByName("strings.xml", scope).toList()
    }

    /**
     * Определяет язык из пути к файлу.
     *
     * Примеры:
     * - res/values/strings.xml -> "default"
     * - res/values-ru/strings.xml -> "ru"
     * - composeResources/values-en/strings.xml -> "en"
     */
    fun extractLanguage(file: VirtualFile): String {
        val path = file.path

        val regex = Regex("""/values-([a-z]{2}(?:-r[A-Z]{2})?)/""")
        val match = regex.find(path)

        return if (match != null) {
            match.groupValues[1].lowercase()
        } else if (path.contains("/values/")) {
            "default"
        } else {
            "unknown"
        }
    }

    /**
     * Строит bundle строк из всех локалей.
     */
    fun buildStringBundle(project: Project, stringName: String): StringResourceBundle? {
        val files = findStringFiles(project)
        if (files.isEmpty()) return null

        val stringsMap = mutableMapOf<String, String>()

        for (file in files) {
            val language = extractLanguage(file)
            if (language == "unknown") continue

            try {
                file.inputStream.use { inputStream ->
                    val strings = StringResourceParser.parse(inputStream, language)
                    strings[stringName]?.let { resource ->
                        stringsMap[language] = resource.value
                    }
                }
            } catch (e: Exception) {
            }
        }

        return if (stringsMap.isNotEmpty()) {
            StringResourceBundle(stringName, stringsMap)
        } else {
            null
        }
    }
}
