package com.theboringdevelopers.commentator.feature.reporter

internal class BugReportGenerator {

    companion object {
        private val PROMPT_TEMPLATE = """
Ты ассистент для форматирования багов. Преобразуй описание в строгий JSON формат.

КРИТИЧЕСКИ ВАЖНО:
1. Верни ТОЛЬКО валидный JSON
2. Никакого текста, markdown (```), комментариев
3. Все поля - строки в двойных кавычках
4. Для переносов строк используй \\n (два символа: обратный слэш и n)

JSON СТРУКТУРА:
{
  "environment": "",
  "buildNumber": "",
  "phoneModel": "Any",
  "osVersion": "Any",
  "car": "",
  "requirementLink": "",
  "preconditions": "",
  "stepsToReproduce": "Шаг 1\\nШаг 2\\nШаг 3",
  "actualResult": "",
  "expectedResult": "",
  "additionalInfo": ""
}

ПРАВИЛА:
- stepsToReproduce: разбей на шаги, используй \\n между шагами
- Если информации нет - пустая строка ""
- НЕ используй markdown блоки

ПРИМЕР 1:
Описание: "При удалении последнего элемента и добавлении нового исчезает виджет на главной странице"
Ответ:
{"environment":"","buildNumber":"","phoneModel":"Any","osVersion":"Any","car":"","requirementLink":"","preconditions":"В списке должен быть один элемент","stepsToReproduce":"Удалить единственный элемент\\nДобавить новый элемент\\nОткрыть главную страницу","actualResult":"Виджет исчезает с главной страницы","expectedResult":"Виджет остается на главной странице","additionalInfo":""}

ПРИМЕР 2:
Описание: "На проде в версии 1.2.3.456 при открытии профиля происходит краш"
Ответ:
{"environment":"prod","buildNumber":"1.2.3.456","phoneModel":"Any","osVersion":"Any","car":"","requirementLink":"","preconditions":"","stepsToReproduce":"Открыть профиль","actualResult":"Приложение крашится","expectedResult":"Профиль открывается корректно","additionalInfo":""}
""".trimIndent()
    }

    fun buildPrompt(userDescription: String): String = """
$PROMPT_TEMPLATE

Описание: "$userDescription"
Ответ:
""".trimIndent()

    fun parseBugReport(response: String): BugReport? {
        return try {
            println("=== RAW RESPONSE ===")
            println(response)
            println("===================")

            var cleaned = response.trim()

            // 1. Удаляем markdown блоки
            cleaned = cleaned.replace(Regex("```json\\s*"), "")
            cleaned = cleaned.replace(Regex("```\\s*"), "")

            // 2. Находим JSON
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')

            if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
                println("ERROR: No valid JSON found")
                return null
            }

            cleaned = cleaned.substring(jsonStart, jsonEnd + 1)

            // 3. КРИТИЧЕСКИ ВАЖНО: Исправляем неправильные переносы строк
            // Модель иногда генерирует "текст\ продолжение" вместо "текст\\n продолжение"
            // Заменяем "...текст\[перенос строки]продолжение" на "...текст\\nпродолжение"
            cleaned = cleaned.replace(Regex("\\\\\n\\s*"), "\\\\n")

            // 4. Убираем реальные переносы строк внутри значений (между кавычками)
            cleaned = cleaned.replace(Regex("\"([^\"]*?)\\n\\s*([^\"]*?)\"")) { matchResult ->
                "\"${matchResult.groupValues[1]}\\n${matchResult.groupValues[2]}\""
            }

            println("=== CLEAN JSON ===")
            println(cleaned)
            println("==================")

            BugReport.fromJson(cleaned)
        } catch (e: Exception) {
            println("ERROR parsing: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}