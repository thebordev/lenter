package com.theboringdevelopers.lenter.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.Messages
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBButton
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

class CommentatorSettingsConfigurable : Configurable {
    private val ollamaSettings = CommentatorSettingsState.getInstance()
    private val jiraSettings = JiraSettingsState.getInstance()

    private val apiUrlField = JBTextField(ollamaSettings.ollamaApiUrl)
    private val modelField = JBTextField(ollamaSettings.modelName)
    private val timeoutField = JBIntSpinner(ollamaSettings.requestTimeoutSeconds, 10, 600)

    private val jiraUrlField = JBTextField(jiraSettings.jiraUrl)
    private val jiraUsernameField = JBTextField(jiraSettings.jiraUsername)
    private val jiraApiTokenField = JBPasswordField().apply {
        text = jiraSettings.jiraApiToken
    }
    private val jiraProjectIdField = JBTextField(jiraSettings.jiraProjectId)
    private val jiraProjectKeyField = JBTextField(jiraSettings.jiraProjectKey)
    private val jiraIssueTypeField = JBTextField(jiraSettings.jiraIssueType)
    private val jiraPriorityField = JBTextField(jiraSettings.jiraPriority)

    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addLabeledComponent("Ollama API URL:", apiUrlField, 1, false)
        .addTooltip("URL Ollama API (например: http://localhost:11434)")
        .addLabeledComponent("Model name:", modelField, 1, false)
        .addTooltip("Название модели (например: qwen2.5-coder:7b, llama3.1:8b)")
        .addLabeledComponent("Timeout (seconds):", timeoutField, 1, false)
        .addTooltip("Максимальное время ожидания ответа от Ollama")

        .addVerticalGap(20)
        .addComponent(JSeparator())
        .addVerticalGap(15)

        .addComponent(JBLabel("<html><b>Jira Settings</b></html>"))
        .addComponent(
            JBButton("Загрузить из local.properties").apply {
                addActionListener { loadJiraSettingsFromLocalProperties() }
            }
        )
        .addVerticalGap(5)
        .addLabeledComponent("Jira URL:", jiraUrlField, 1, false)
        .addTooltip("URL вашего Jira сервера (например: https://jira.company.com)")
        .addLabeledComponent("Username:", jiraUsernameField, 1, false)
        .addTooltip("Ваше имя пользователя в Jira (для отображения в логах)")
        .addLabeledComponent("Personal Access Token:", jiraApiTokenField, 1, false)
        .addComponent(
            HyperlinkLabel("Создайте Personal Access Token в Jira").apply {
                addHyperlinkListener {
                    val url = jiraUrlField.text.trim().ifEmpty { "https://your-jira-instance.com" }
                    setHyperlinkTarget("$url/secure/ViewProfile.jspa?selectedTab=com.atlassian.pats.pats-plugin:jira-user-personal-access-tokens")
                }
                border = JBUI.Borders.emptyLeft(160)
            }
        )
        .addComponent(
            JBLabel("<html><small><b>ВАЖНО:</b> Используйте Personal Access Token, НЕ API Token!<br>" +
                    "Token должен иметь права на создание задач в проекте.<br>" +
                    "Документация: <a href='https://confluence.atlassian.com/enterprise/using-personal-access-tokens-1026032365.html'>Atlassian Docs</a></small></html>").apply {
                border = JBUI.Borders.empty(5, 160, 0, 0)
            }
        )
        .addVerticalGap(10)
        .addLabeledComponent("Project ID:", jiraProjectIdField, 1, false)
        .addTooltip("ID проекта в Jira (числовое значение, например: 00000)")
        .addLabeledComponent("Project Key:", jiraProjectKeyField, 1, false)
        .addTooltip("Ключ проекта (например: TBD)")
        .addLabeledComponent("Issue Type ID:", jiraIssueTypeField, 1, false)
        .addTooltip("ID типа задачи (10101 = Bug, 00000 = Task)")
        .addLabeledComponent("Default Priority ID:", jiraPriorityField, 1, false)
        .addTooltip("ID приоритета по умолчанию (1 = Highest, 2 = High, 3 = Medium, 4 = Low, 5 = Lowest)")

        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun createComponent(): JComponent = panel

    override fun isModified(): Boolean {
        return apiUrlField.text.trim() != ollamaSettings.ollamaApiUrl ||
                modelField.text.trim() != ollamaSettings.modelName ||
                timeoutField.number != ollamaSettings.requestTimeoutSeconds ||
                jiraUrlField.text.trim() != jiraSettings.jiraUrl ||
                jiraUsernameField.text.trim() != jiraSettings.jiraUsername ||
                String(jiraApiTokenField.password) != jiraSettings.jiraApiToken ||
                jiraProjectIdField.text.trim() != jiraSettings.jiraProjectId ||
                jiraProjectKeyField.text.trim() != jiraSettings.jiraProjectKey ||
                jiraIssueTypeField.text.trim() != jiraSettings.jiraIssueType ||
                jiraPriorityField.text.trim() != jiraSettings.jiraPriority
    }

    override fun apply() {
        val timeout = timeoutField.number
        if (timeout <= 0) {
            throw ConfigurationException("Timeout должен быть больше нуля")
        }

        val apiUrl = apiUrlField.text.trim()
        if (apiUrl.isBlank()) {
            throw ConfigurationException("Ollama API URL не может быть пустым")
        }

        val model = modelField.text.trim()
        if (model.isBlank()) {
            throw ConfigurationException("Model name не может быть пустым")
        }

        val jiraUrl = jiraUrlField.text.trim()
        val jiraToken = String(jiraApiTokenField.password).trim()

        if (jiraUrl.isNotBlank() && !jiraUrl.startsWith("http")) {
            throw ConfigurationException("Jira URL должен начинаться с http:// или https://")
        }

        if (jiraUrl.isNotBlank() && jiraToken.isBlank()) {
            throw ConfigurationException("Укажите Personal Access Token для работы с Jira")
        }

        ollamaSettings.ollamaApiUrl = apiUrl
        ollamaSettings.modelName = model
        ollamaSettings.requestTimeoutSeconds = timeout

        jiraSettings.jiraUrl = jiraUrl.trimEnd('/')
        jiraSettings.jiraUsername = jiraUsernameField.text.trim()
        jiraSettings.jiraApiToken = jiraToken
        jiraSettings.jiraProjectId = jiraProjectIdField.text.trim()
        jiraSettings.jiraProjectKey = jiraProjectKeyField.text.trim()
        jiraSettings.jiraIssueType = jiraIssueTypeField.text.trim()
        jiraSettings.jiraPriority = jiraPriorityField.text.trim()
    }

    override fun reset() {
        apiUrlField.text = ollamaSettings.ollamaApiUrl
        modelField.text = ollamaSettings.modelName
        timeoutField.number = ollamaSettings.requestTimeoutSeconds

        jiraUrlField.text = jiraSettings.jiraUrl
        jiraUsernameField.text = jiraSettings.jiraUsername
        jiraApiTokenField.text = jiraSettings.jiraApiToken
        jiraProjectIdField.text = jiraSettings.jiraProjectId
        jiraProjectKeyField.text = jiraSettings.jiraProjectKey
        jiraIssueTypeField.text = jiraSettings.jiraIssueType
        jiraPriorityField.text = jiraSettings.jiraPriority
    }

    override fun getDisplayName(): String = "Lenter"

    private fun loadJiraSettingsFromLocalProperties() {
        val defaults = jiraSettings.readLocalProperties()

        if (defaults == null) {
            Messages.showWarningDialog(
                panel,
                "local.properties не найден или не содержит Jira настройки.",
                "Не удалось загрузить настройки",
            )
            return
        }

        defaults.jiraUrl?.let { jiraUrlField.text = it }
        defaults.jiraUsername?.let { jiraUsernameField.text = it }
        defaults.jiraApiToken?.let { jiraApiTokenField.text = it }
        defaults.jiraProjectId?.let { jiraProjectIdField.text = it }
        defaults.jiraProjectKey?.let { jiraProjectKeyField.text = it }
        defaults.jiraIssueType?.let { jiraIssueTypeField.text = it }
        defaults.jiraPriority?.let { jiraPriorityField.text = it }

        Messages.showInfoMessage(
            panel,
            "Настройки Jira загружены из ${defaults.origin}.",
            "Настройки обновлены",
        )
    }
}
