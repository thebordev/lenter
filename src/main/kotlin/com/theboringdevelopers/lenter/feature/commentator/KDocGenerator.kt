package com.theboringdevelopers.lenter.feature.commentator

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

internal class KDocGenerator {

    companion object {
        private val PROMPT_HEADER = """
Сгенерируй описание на русском для Kotlin кода.

ПРАВИЛА:
1. Свойство (val/var) → одна строка описания
2. Функция без параметров → одна строка описания
3. Функция с параметрами → описание + пустая строка + @param для каждого параметра
4. Функция с return → добавь @return (только если НЕ Unit)
5. Класс → описание + @property для val/var параметров конструктора

ФОРМАТ:
- Описания с заглавной буквы, без точки
- @param/@property/@return с маленькой буквы, без точки
- БЕЗ /** */ и звездочек
- БЕЗ кода, БЕЗ примеров, БЕЗ метаописаний

ПРИМЕР 1:
Код: val timeout = 5000
Ответ:
Время ожидания в миллисекундах

ПРИМЕР 2:
Код: fun reset()
Ответ:
Сбрасывает состояние на начальное

ПРИМЕР 3:
Код: fun pay(amount: Int, currency: String)
Ответ:
Выполняет платеж

@param amount сумма платежа
@param currency валюта платежа

ПРИМЕР 4:
Код: class User(val id: Int, val name: String)
Ответ:
Пользователь системы

@property id идентификатор
@property name имя
""".trimIndent()
    }

    fun buildPrompt(code: String, isProperty: Boolean = false, hasReturnValue: Boolean = true): String {
        return """
$PROMPT_HEADER

Код:
${code.trim()}

Ответ:
""".trimIndent()
    }

    fun sanitizeResponse(
        raw: String,
        isProperty: Boolean = false,
        hasReturnValue: Boolean = true,
        decl: KtDeclaration? = null
    ): String {
        var text = raw.replace("\r", "").trim()

        text = text.substringAfter("Ответ:", text)
            .substringAfter("ответ:", text)
            .trim()

        text = text.replace(Regex("```[a-zA-Z]*"), "")
        text = text.replace("```", "")
        text = text.replace("/**", "").replace("*/", "")

        text = text.replace(Regex("\\\\u[0-9a-fA-F]{4}"), "")

        var lines = text.lines()
            .map { line ->
                var trimmed = line.trimStart()

                while (trimmed.startsWith("*")) {
                    trimmed = if (trimmed.startsWith("* ")) {
                        trimmed.substring(2)
                    } else {
                        trimmed.substring(1).trimStart()
                    }
                }

                trimmed.trimEnd()
            }

        while (lines.isNotEmpty() && lines.first().isBlank()) {
            lines = lines.drop(1)
        }
        while (lines.isNotEmpty() && lines.last().isBlank()) {
            lines = lines.dropLast(1)
        }

        val realParams = extractParameterNames(decl)

        lines = lines.filter { line ->
            val trimmed = line.trim().lowercase()

            when {
                trimmed.isBlank() -> true

                trimmed.contains("here is") -> false
                trimmed.contains("вот kdoc") -> false
                trimmed.contains("вот.generated") -> false
                trimmed.startsWith("generated") -> false
                trimmed.startsWith("описание") && trimmed.endsWith(":") -> false
                trimmed.startsWith("краткое описание:") -> false
                trimmed.startsWith("код:") -> false
                trimmed.startsWith("ответ:") -> false
                trimmed.startsWith("для:") -> false
                trimmed.startsWith("пример") -> false
                trimmed.contains("---") -> false
                trimmed.startsWith("внимание:") -> false
                trimmed.startsWith("правила:") -> false
                trimmed.startsWith("формат:") -> false
                trimmed.startsWith("свойства:") -> false
                trimmed.startsWith("параметры:") -> false
                trimmed.startsWith("возвращаемое значение:") -> false

                trimmed.startsWith("#") -> false
                trimmed.startsWith("**") -> false
                trimmed.startsWith("###") -> false

                trimmed.contains("class ") && trimmed.contains("(") -> false
                trimmed.contains("fun ") && trimmed.contains("(") -> false
                trimmed.contains("val ") && trimmed.contains("=") -> false
                trimmed.contains("var ") && trimmed.contains("=") -> false
                trimmed.contains(" -> ") -> false
                trimmed.contains("viewmodelscope") -> false
                trimmed.contains(".launch") -> false
                trimmed.contains(".emit") -> false
                trimmed.contains("when (") -> false
                trimmed.contains("if (") -> false
                trimmed.contains("private fun") -> false
                trimmed.contains("internal class") -> false

                trimmed == "/**" || trimmed == "*/" -> false
                trimmed == "(теги добавлены" -> false

                isProperty && line.trim().startsWith("@") -> false

                line.trim().startsWith("@param") -> {
                    if (isProperty) return@filter false
                    val paramName = line.trim()
                        .removePrefix("@param")
                        .trim()
                        .substringBefore(" ")
                        .substringBefore("-")
                        .trim()
                    paramName.isNotEmpty() && paramName in realParams
                }

                line.trim().startsWith("@property") -> {
                    if (isProperty) return@filter false
                    val propertyName = line.trim()
                        .removePrefix("@property")
                        .trim()
                        .substringBefore(" ")
                        .substringBefore("-")
                        .trim()
                    propertyName.isNotEmpty() && propertyName in realParams
                }

                line.trim().startsWith("@return") -> {
                    if (isProperty || !hasReturnValue) return@filter false
                    val returnDesc = line.trim()
                        .removePrefix("@return")
                        .trim()
                        .lowercase()
                    when {
                        returnDesc.isEmpty() -> false
                        returnDesc in listOf("nothing", "unit", "void", "нет", "н/д") -> false
                        returnDesc.startsWith("true") || returnDesc.startsWith("false") -> true
                        returnDesc.startsWith("нет") -> false
                        else -> true
                    }
                }

                else -> true
            }
        }

        if (isProperty) {
            val firstNonEmpty = lines.firstOrNull { it.isNotBlank() } ?: return ""
            return cleanupLine(firstNonEmpty)
        }

        return lines.joinToString("\n") { cleanupLine(it) }
    }

    private fun cleanupLine(line: String): String {
        var result = line.trim()

        if (result.endsWith(".") && !result.endsWith("..")) {
            result = result.dropLast(1)
        }

        result = result.replace(Regex("\\s+"), " ")

        return result
    }

    private fun extractParameterNames(decl: KtDeclaration?): Set<String> {
        return when (decl) {
            is KtNamedFunction -> {
                decl.valueParameters.mapNotNull { it.name }.toSet()
            }
            is KtClass -> {
                decl.primaryConstructor?.valueParameters?.mapNotNull { it.name }?.toSet() ?: emptySet()
            }
            else -> emptySet()
        }
    }

    fun validateAndFixComment(
        text: String,
        isProperty: Boolean = false,
        hasReturnValue: Boolean = true
    ): String {
        if (text.isBlank()) return ""

        if (isProperty) {
            val clean = text.lines()
                .filter { it.isNotBlank() }
                .firstOrNull { !it.trimStart().startsWith("@") }
                ?: return ""

            return clean.trim().replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }

        var lines = text.lines().toMutableList()
        if (lines.isEmpty()) return ""

        if (!hasReturnValue) {
            lines = lines.filter { !it.trim().startsWith("@return") }.toMutableList()
        }

        val result = mutableListOf<String>()
        var prevEmpty = false
        for (line in lines) {
            val isEmpty = line.isBlank()
            if (isEmpty && prevEmpty) continue
            result.add(line)
            prevEmpty = isEmpty
        }
        lines = result

        val firstNonEmptyIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstNonEmptyIndex == -1) return ""

        if (!lines[firstNonEmptyIndex].trimStart().startsWith("@")) {
            var fixed = lines[firstNonEmptyIndex].trim()
            if (fixed.endsWith(".") && !fixed.endsWith("..")) {
                fixed = fixed.dropLast(1)
            }
            fixed = fixed.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            lines[firstNonEmptyIndex] = fixed
        }

        for (i in lines.indices) {
            val line = lines[i]
            if (line.trimStart().startsWith("@") && line.endsWith(".") && !line.endsWith("..")) {
                lines[i] = line.dropLast(1)
            }
        }

        val firstTagIndex = lines.indexOfFirst { it.trimStart().startsWith("@") }
        if (firstTagIndex > 0 && firstTagIndex == firstNonEmptyIndex + 1) {
            lines.add(firstTagIndex, "")
        }

        return lines.joinToString("\n")
    }

    fun formatComment(content: String, indent: String): String {
        if (content.isBlank()) return ""

        val lines = content.lines()
        val trimmedLines = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

        if (trimmedLines.isEmpty()) return ""

        return if (trimmedLines.size == 1) {
            "$indent/** ${trimmedLines.first().trim()} */"
        } else {
            buildString {
                append(indent).append("/**\n")
                trimmedLines.forEach {
                    append(indent).append(" * ").append(it.trimEnd()).append('\n')
                }
                append(indent).append(" */")
            }
        }
    }

    fun isClassProperty(decl: KtDeclaration): Boolean {
        return decl is KtProperty
    }

    fun hasReturnValue(decl: KtDeclaration): Boolean {
        if (decl !is KtNamedFunction) return true
        val typeRef = decl.typeReference
        if (typeRef != null) {
            val typeText = typeRef.text
            return typeText != "Unit" && typeText.isNotEmpty()
        }
        return false
    }
}