package com.theboringdevelopers.commentator.ollama

import com.intellij.openapi.diagnostic.Logger
import com.theboringdevelopers.commentator.settings.CommentatorSettingsState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OllamaClient(
    private val settings: CommentatorSettingsState,
) {
    private val logger = Logger.getInstance(OllamaClient::class.java)

    fun generate(prompt: String): Result {
        val baseUrl = settings.ollamaApiUrl.ifBlank { "http://localhost:11434" }
        val model = settings.modelName.ifBlank { "qwen3:8b" }
        val timeoutSeconds = settings.requestTimeoutSeconds.coerceAtLeast(10)

        return try {
            val url = URL("$baseUrl/api/generate")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = timeoutSeconds * 1000

            val jsonRequest = buildString {
                append("{")
                append("\"model\":\"$model\",")
                append("\"prompt\":${escapeJson(prompt)},")
                append("\"stream\":false")
                append("}")
            }

            logger.info("Sending request to Ollama at $baseUrl with model $model")

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonRequest)
                writer.flush()
            }

            val responseCode = connection.responseCode
            logger.info("Response code: $responseCode")

            if (responseCode == 200) {
                val response = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }

                logger.info("Response received, length: ${response.length}")

                val generatedText = parseResponse(response)

                if (generatedText.isBlank()) {
                    Result.Error("Ollama вернула пустой ответ")
                } else {
                    logger.info("Generated text length: ${generatedText.length}")
                    Result.Success(generatedText)
                }
            } else {
                val errorText = connection.errorStream?.let { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } ?: "Unknown error"

                logger.error("Ollama API error: $errorText")
                Result.Error("Ollama API error (код $responseCode): $errorText")
            }
        } catch (e: java.net.ConnectException) {
            logger.error("Cannot connect to Ollama", e)
            Result.Error("Не удалось подключиться к Ollama. Убедитесь, что Ollama запущена (ollama serve)")
        } catch (e: java.net.SocketTimeoutException) {
            logger.error("Ollama timeout", e)
            Result.Error("Превышено время ожидания ответа от Ollama")
        } catch (e: Exception) {
            logger.error("Ollama request failed", e)
            Result.Error("Ошибка запроса к Ollama: ${e.message}")
        }
    }

    private fun parseResponse(json: String): String {
        val responseField = "\"response\":\""
        val startIdx = json.indexOf(responseField)
        if (startIdx == -1) {
            logger.warn("Cannot find 'response' field in JSON")
            return ""
        }

        val textStart = startIdx + responseField.length
        var textEnd = textStart
        var escaped = false

        while (textEnd < json.length) {
            val char = json[textEnd]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                break
            }
            textEnd++
        }

        if (textEnd >= json.length) {
            logger.warn("Cannot parse JSON response")
            return ""
        }

        val rawText = json.substring(textStart, textEnd)

        return rawText
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun escapeJson(text: String): String {
        return buildString {
            append('"')
            for (char in text) {
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> {
                        if (char.code < 32) {
                            append("\\u${char.code.toString(16).padStart(4, '0')}")
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Error(val message: String) : Result
    }
}