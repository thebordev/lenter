package com.theboringdevelopers.lenter.feature.preview.string

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Парсер strings.xml файлов.
 *
 * Поддерживает Android и Compose Multiplatform форматы.
 */
object StringResourceParser {

    /**
     * Парсит strings.xml файл.
     *
     * @param inputStream поток данных XML файла
     * @param language код языка (default, ru, en и т.д.)
     * @return карта имя -> значение
     */
    fun parse(inputStream: InputStream, language: String): Map<String, StringResource> {
        val result = mutableMapOf<String, StringResource>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(inputStream)
            doc.documentElement.normalize()

            val root = doc.documentElement
            if (root.nodeName != "resources") {
                return emptyMap()
            }

            val stringNodes = root.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i) as? Element ?: continue

                val name = node.getAttribute("name")
                if (name.isBlank()) continue

                val value = node.textContent ?: continue

                val placeholders = extractPlaceholders(value)

                result[name] = StringResource(
                    name = name,
                    value = value,
                    language = language,
                    hasPlaceholders = placeholders.isNotEmpty(),
                    placeholders = placeholders
                )
            }
        } catch (e: Exception) {
        }

        return result
    }

    /**
     * Извлекает плейсхолдеры из строки.
     * Поддерживает: %s, %d, %1$s, {0}, {name}
     */
    private fun extractPlaceholders(value: String): List<String> {
        val placeholders = mutableListOf<String>()

        Regex("""%[0-9]*\$?[sdf]""").findAll(value).forEach {
            placeholders.add(it.value)
        }

        Regex("""\{([^}]+)\}""").findAll(value).forEach {
            placeholders.add(it.value)
        }

        return placeholders
    }
}
