package com.theboringdevelopers.lenter.feature.jira

import com.theboringdevelopers.lenter.settings.states.JiraSettingsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

class JiraCommentClient(
    private val settings: JiraSettingsState,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Serializable
    data class AddCommentRequest(
        val body: String
    )

    sealed class Result {
        data class Success(val commentUrl: String) : Result()
        data class Error(val message: String, val statusCode: Int = 0) : Result()
    }

    fun addComment(issueKey: String, commentText: String): Result {
        return try {
            if (settings.jiraApiToken.isBlank()) {
                return Result.Error("Настройте Jira API Token в Settings → Tools → Commentator")
            }

            val request = AddCommentRequest(body = commentText)
            val requestBody = json.encodeToString(AddCommentRequest.serializer(), request)

            println("=== JIRA ADD COMMENT ===")
            println("URL: ${settings.jiraUrl}/rest/api/2/issue/$issueKey/comment")
            println("Body: $requestBody")
            println("========================")

            val url = URI.create("${settings.jiraUrl.trimEnd('/')}/rest/api/2/issue/$issueKey/comment").toURL()
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${settings.jiraApiToken}")

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            println("Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                println("Success Response: $response")

                val commentUrl = "${settings.jiraUrl}/browse/$issueKey"
                Result.Success(commentUrl = commentUrl)
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }

                println("Error Response: $errorBody")

                val errorMessage = when (responseCode) {
                    401 -> "Ошибка авторизации. Проверьте Personal Access Token"
                    403 -> "Нет прав на добавление комментариев к задаче $issueKey"
                    404 -> "Задача $issueKey не найдена"
                    else -> "HTTP $responseCode: $errorBody"
                }

                Result.Error(errorMessage, responseCode)
            }
        } catch (e: Exception) {
            println("Exception: ${e.message}")
            e.printStackTrace()
            Result.Error("Ошибка подключения: ${e.message}")
        }
    }
}