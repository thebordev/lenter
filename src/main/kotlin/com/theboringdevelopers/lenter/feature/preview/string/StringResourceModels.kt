package com.theboringdevelopers.lenter.feature.preview.string

/**
 * Данные строкового ресурса.
 */
data class StringResource(
    val name: String,
    val value: String,
    val language: String,
    val hasPlaceholders: Boolean = false,
    val placeholders: List<String> = emptyList(),
)

/**
 * Коллекция строк для одного имени в разных локалях.
 */
data class StringResourceBundle(
    val name: String,
    val strings: Map<String, String>,
) {
    /**
     * Получает значение для указанного языка с fallback на default.
     */
    fun getValue(preferredLanguage: String): String? {
        return strings[preferredLanguage]
            ?: strings["default"]
            ?: strings.values.firstOrNull()
    }

    /**
     * Получает язык, на котором будет показано значение.
     */
    fun getActualLanguage(preferredLanguage: String): String {
        return when {
            strings.containsKey(preferredLanguage) -> preferredLanguage
            strings.containsKey("default") -> "default"
            else -> strings.keys.first()
        }
    }
}
