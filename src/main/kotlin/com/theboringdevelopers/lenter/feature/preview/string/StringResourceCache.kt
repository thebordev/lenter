package com.theboringdevelopers.lenter.feature.preview.string

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Кэш строковых ресурсов.
 */
@Service(Service.Level.PROJECT)
class StringResourceCache {

    private data class CacheEntry(
        val modificationStamp: Long,
        val bundleRef: SoftReference<StringResourceBundle?>
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private var lastGlobalStamp: Long = 0
    private val globalCache = SoftReference<Map<String, StringResourceBundle>>(null)

    /**
     * Получает bundle для указанной строки.
     */
    fun getStringBundle(project: Project, stringName: String): StringResourceBundle? {
        val currentStamp = calculateGlobalModificationStamp(project)

        if (currentStamp == lastGlobalStamp) {
            globalCache.get()?.get(stringName)?.let { return it }
        }

        cache[stringName]?.let { entry ->
            if (entry.modificationStamp == currentStamp) {
                entry.bundleRef.get()?.let { return it }
            }
        }

        val bundle = StringResourceLookup.buildStringBundle(project, stringName)

        if (bundle != null) {
            cache[stringName] = CacheEntry(currentStamp, SoftReference(bundle))
        }

        return bundle
    }

    /**
     * Вычисляет суммарный modification stamp всех strings.xml файлов.
     */
    private fun calculateGlobalModificationStamp(project: Project): Long {
        val files = StringResourceLookup.findStringFiles(project)
        return files.sumOf { it.modificationStamp }
    }
}
