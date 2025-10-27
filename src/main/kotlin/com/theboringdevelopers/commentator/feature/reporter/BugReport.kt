package com.theboringdevelopers.commentator.feature.reporter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BugReport(
    val environment: String = "",
    val buildNumber: String = "",
    val phoneModel: String = "Any",
    val osVersion: String = "Any",
    val car: String = "",
    val requirementLink: String = "",
    val preconditions: String = "",
    val stepsToReproduce: String = "",
    val actualResult: String = "",
    val expectedResult: String = "",
    val additionalInfo: String = ""
) {
    fun toHtmlTable(): String {
        fun escape(text: String) = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        return """
        <html>
        <head>
            <style>
                body { 
                    font-family: 'Segoe UI', Arial, sans-serif; 
                    padding: 10px;
                }
                table { 
                    border-collapse: collapse; 
                    width: 100%; 
                    margin: 10px 0;
                    background-color: white;
                }
                th, td { 
                    border: 1px solid #ccc; 
                    padding: 10px; 
                    text-align: left;
                    vertical-align: top;
                }
                th { 
                    background-color: #e8e8e8; 
                    font-weight: bold;
                    width: 35%;
                }
                td { 
                    white-space: pre-wrap;
                }
                .empty-row td {
                    background-color: #f9f9f9;
                    height: 5px;
                    padding: 2px;
                }
            </style>
        </head>
        <body>
            <table>
                <tr><th>Поле</th><th>Значение</th></tr>
                <tr><td>⭐ Окружение (prod/stage/dev)</td><td>${escape(environment).ifEmpty { "&nbsp;" }}</td></tr>
                <tr><td>⭐ Номер сборки</td><td>${escape(buildNumber).ifEmpty { "&nbsp;" }}</td></tr>
                <tr><td>⭐ Телефон (модель)</td><td>${escape(phoneModel)}</td></tr>
                <tr><td>⭐ Версия ОС</td><td>${escape(osVersion)}</td></tr>
                <tr><td>Автомобиль (если важно)</td><td>${escape(car).ifEmpty { "&nbsp;" }}</td></tr>
                <tr><td>Ссылка на требование (если важно)</td><td>${escape(requirementLink).ifEmpty { "&nbsp;" }}</td></tr>
                <tr class="empty-row"><td colspan="2">&nbsp;</td></tr>
                <tr><td>💡 Предусловия</td><td>${escape(preconditions).ifEmpty { "&nbsp;" }}</td></tr>
                <tr class="empty-row"><td colspan="2">&nbsp;</td></tr>
                <tr><td>ℹ️ Шаги воспроизведения</td><td>${
            escape(stepsToReproduce).replace("\n", "<br>").ifEmpty { "&nbsp;" }
        }</td></tr>
                <tr class="empty-row"><td colspan="2">&nbsp;</td></tr>
                <tr><td>❌ Фактический результат</td><td>${escape(actualResult).ifEmpty { "&nbsp;" }}</td></tr>
                <tr><td>💡 Ожидаемый результат</td><td>${escape(expectedResult).ifEmpty { "&nbsp;" }}</td></tr>
                <tr><td>❓ Дополнительная информация</td><td>${escape(additionalInfo).ifEmpty { "&nbsp;" }}</td></tr>
            </table>
        </body>
        </html>
    """.trimIndent()
    }

    fun toJiraTable(): String {
        // Confluence Wiki Markup эмодзи:
        // (*) = желтая звезда
        // (!) = лампочка (предусловия)
        // (i) = информация (шаги)
        // (x) = крестик/ошибка (фактический результат)
        // (on) = лампочка горящая (ожидаемый результат)
        // (?) = вопрос (доп. информация)

        // Форматируем предусловия как список
        val preconditionsFormatted = if (preconditions.isNotEmpty()) {
            preconditions.split("\n")
                .filter { it.isNotBlank() }
                .joinToString("\n") { "* $it" }
        } else {
            "* \u00A0" // неразрывный пробел
        }

        // Форматируем шаги как нумерованный список
        val stepsFormatted = if (stepsToReproduce.isNotEmpty()) {
            stepsToReproduce.split("\n")
                .filter { it.isNotBlank() }
                .joinToString("\n") { "# $it" }
        } else {
            "# \u00A0"
        }

        return """
||Поле||Значение||
||(*) Окружение _(prod/stage/dev)_||${if (environment.isNotEmpty()) "_${environment}_" else "\u00A0"}||
||(*) Номер сборки||${buildNumber.ifEmpty { "\u00A0" }}||
||(*) Телефон _(модель)_||${phoneModel.ifEmpty { "\u00A0" }}||
||(*) Версия ОС||${osVersion.ifEmpty { "\u00A0" }}||
||Автомобиль (_если важно_)||${car.ifEmpty { "\u00A0" }}||
||Ссылка на требование _(если важно)_||${requirementLink.ifEmpty { "\u00A0" }}||
||\u00A0||\u00A0||
||*(!) Предусловия*||$preconditionsFormatted||
||*(i) Шаги воспроизведения*||$stepsFormatted||
||*(x) Фактический результат*||${actualResult.ifEmpty { "\u00A0" }}||
||*(on) Ожидаемый результат*||${expectedResult.ifEmpty { "\u00A0" }}||
||_(?) Дополнительная информация_||${additionalInfo.ifEmpty { "\u00A0" }}||

\u00A0
        """.trimIndent()
    }

    fun toPlainTextTable(): String {
        val maxKeyLength = 35

        fun formatRow(key: String, value: String): String {
            val lines = value.split("\n")
            return if (lines.size == 1) {
                "| ${key.padEnd(maxKeyLength)} | ${lines[0]}"
            } else {
                val first = "| ${key.padEnd(maxKeyLength)} | ${lines[0]}"
                val rest = lines.drop(1).joinToString("\n") {
                    "| ${" ".repeat(maxKeyLength)} | $it"
                }
                "$first\n$rest"
            }
        }

        val separator = "+" + "-".repeat(maxKeyLength + 2) + "+" + "-".repeat(50) + "+"

        return """
$separator
${formatRow("Поле", "Значение")}
$separator
${formatRow("⭐ Окружение (prod/stage/dev)", environment)}
${formatRow("⭐ Номер сборки", buildNumber)}
${formatRow("⭐ Телефон (модель)", phoneModel)}
${formatRow("⭐ Версия ОС", osVersion)}
${formatRow("Автомобиль (если важно)", car)}
${formatRow("Ссылка на требование", requirementLink)}
$separator
${formatRow("💡 Предусловия", preconditions)}
$separator
${formatRow("ℹ️ Шаги воспроизведения", stepsToReproduce)}
$separator
${formatRow("❌ Фактический результат", actualResult)}
${formatRow("💡 Ожидаемый результат", expectedResult)}
${formatRow("❓ Дополнительная информация", additionalInfo)}
$separator
        """.trimIndent()
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun fromJson(jsonString: String): BugReport? {
            return try {
                json.decodeFromString<BugReport>(jsonString)
            } catch (e: Exception) {
                println("kotlinx.serialization failed, trying manual parsing: ${e.message}")
                fromJsonManual(jsonString)
            }
        }

        private fun fromJsonManual(jsonString: String): BugReport? {
            return try {
                val fields = mutableMapOf<String, String>()

                // Простой regex парсер для извлечения полей
                val pattern = """"(\w+)"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()

                pattern.findAll(jsonString).forEach { match ->
                    val key = match.groupValues[1]
                    val value = match.groupValues[2]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    fields[key] = value
                }

                BugReport(
                    environment = fields["environment"] ?: "",
                    buildNumber = fields["buildNumber"] ?: "",
                    phoneModel = fields["phoneModel"] ?: "Any",
                    osVersion = fields["osVersion"] ?: "Any",
                    car = fields["car"] ?: "",
                    requirementLink = fields["requirementLink"] ?: "",
                    preconditions = fields["preconditions"] ?: "",
                    stepsToReproduce = fields["stepsToReproduce"] ?: "",
                    actualResult = fields["actualResult"] ?: "",
                    expectedResult = fields["expectedResult"] ?: "",
                    additionalInfo = fields["additionalInfo"] ?: ""
                )
            } catch (e: Exception) {
                println("Manual parsing also failed: ${e.message}")
                null
            }
        }
    }
}
