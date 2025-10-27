package com.theboringdevelopers.lenter.feature.jira

import com.theboringdevelopers.lenter.settings.JiraSettingsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

class JiraClient(private val settings: JiraSettingsState) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Serializable
    data class CreateIssueRequest(
        val fields: Fields
    ) {
        @Serializable
        data class Fields(
            val project: Project,
            val summary: String,
            val description: String,
            val issuetype: IssueType,
            val priority: Priority? = null
        )

        @Serializable
        data class Project(val id: String)

        @Serializable
        data class IssueType(val id: String)

        @Serializable
        data class Priority(val id: String)
    }

    @Serializable
    data class CreateIssueResponse(
        val id: String,
        val key: String,
        val self: String
    )

    sealed class Result {
        data class Success(val issueKey: String, val issueUrl: String) : Result()
        data class Error(val message: String, val statusCode: Int = 0) : Result()
    }

    fun createIssue(summary: String, description: String): Result {
        return try {
            if (settings.jiraApiToken.isBlank()) {
                return Result.Error("Настройте Jira API Token в Settings → Tools → Lenter")
            }

            val request = CreateIssueRequest(
                fields = CreateIssueRequest.Fields(
                    project = CreateIssueRequest.Project(id = settings.jiraProjectId),
                    summary = summary,
                    description = description,
                    issuetype = CreateIssueRequest.IssueType(id = settings.jiraIssueType),
                    priority = CreateIssueRequest.Priority(id = settings.jiraPriority)
                )
            )

            val requestBody = json.encodeToString(CreateIssueRequest.serializer(), request)

            println("=== JIRA REQUEST ===")
            println("URL: ${settings.jiraUrl}/rest/api/2/issue")
            println("Body: $requestBody")
            println("===================")

            val url = URL("${settings.jiraUrl.trimEnd('/')}/rest/api/2/issue")
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

                val issueResponse = json.decodeFromString<CreateIssueResponse>(response)

                Result.Success(
                    issueKey = issueResponse.key,
                    issueUrl = "${settings.jiraUrl}/browse/${issueResponse.key}"
                )
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }

                println("Error Response: $errorBody")

                val errorMessage = when (responseCode) {
                    401 -> """
                        Ошибка авторизации (401).
                        
                        Проверьте:
                        1. Personal Access Token создан и активен
                        2. Token скопирован полностью без пробелов
                        3. У вас есть права на создание задач в проекте ${settings.jiraProjectKey}
                        
                        Создайте новый token: ${settings.jiraUrl}/secure/ViewProfile.jspa
                        
                        Детали: $errorBody
                    """.trimIndent()

                    403 -> "Нет прав на создание задач в проекте ${settings.jiraProjectKey}"
                    404 -> "Проект ${settings.jiraProjectKey} не найден или неправильный URL"
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