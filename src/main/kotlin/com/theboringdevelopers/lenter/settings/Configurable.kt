package com.theboringdevelopers.lenter.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.theboringdevelopers.lenter.settings.states.JiraSettingsState
import com.theboringdevelopers.lenter.settings.states.LinterSettingsState
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState
import java.net.URL
import javax.swing.*

class Configurable : Configurable {

    private companion object {
        const val MIN_TIMEOUT = 10
        const val MAX_TIMEOUT = 600
        const val MIN_ICON_SIZE = 8
        const val MAX_ICON_SIZE = 24
        const val DEFAULT_LABEL_WIDTH = 160
    }

    private val ollamaSettings = LinterSettingsState.getInstance()
    private val jiraSettings = JiraSettingsState.getInstance()
    private val previewSettings = PreviewSettingsState.getInstance()

    // Ollama Settings
    private val apiUrlField = JBTextField()
    private val modelField = JBTextField()
    private val timeoutField = JBIntSpinner(MIN_TIMEOUT, MIN_TIMEOUT, MAX_TIMEOUT)

    // Preview Settings
    private val composeColorPreviewCheckBox = JBCheckBox("Включить предпросмотр цветов Compose")
    private val showColorInGutterCheckBox = JBCheckBox("Показывать иконки цвета в gutter")
    private val colorIconSizeSpinner = JBIntSpinner(12, MIN_ICON_SIZE, MAX_ICON_SIZE)
    private val brushGradientPreviewCheckBox = JBCheckBox("Включить предпросмотр Brush градиентов")
    private val gradientIconSizeSpinner = JBIntSpinner(12, MIN_ICON_SIZE, MAX_ICON_SIZE)

    // Jira Settings
    private val localPropertiesPathField = TextFieldWithBrowseButton().apply {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Выберите local.properties")
            .withDescription("Выберите файл local.properties с настройками Jira")
            .withFileFilter { it.name == "local.properties" || it.extension == "properties" }

        addBrowseFolderListener(null, descriptor)
    }

    private val jiraUrlField = JBTextField()
    private val jiraUsernameField = JBTextField()
    private val jiraApiTokenField = JBPasswordField()
    private val jiraProjectIdField = JBTextField()
    private val jiraProjectKeyField = JBTextField()
    private val jiraIssueTypeField = JBTextField()
    private val jiraPriorityField = JBTextField()

    private val loadFromLocalPropertiesButton = JButton("Загрузить").apply {
        addActionListener { loadJiraSettingsFromLocalProperties() }
        toolTipText = "Загрузить настройки из выбранного файла"
    }

    private val panel: JPanel by lazy { createPanel() }

    override fun createComponent(): JComponent {
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        return apiUrlField.text.trim() != ollamaSettings.ollamaApiUrl ||
                modelField.text.trim() != ollamaSettings.modelName ||
                timeoutField.number != ollamaSettings.requestTimeoutSeconds ||

                composeColorPreviewCheckBox.isSelected != previewSettings.composeColorPreviewEnabled ||

                localPropertiesPathField.text.trim() != jiraSettings.localPropertiesPath ||
                jiraUrlField.text.trim() != jiraSettings.jiraUrl ||
                jiraUsernameField.text.trim() != jiraSettings.jiraUsername ||
                !jiraApiTokenField.password.contentEquals(jiraSettings.jiraApiToken.toCharArray()) ||
                jiraProjectIdField.text.trim() != jiraSettings.jiraProjectId ||
                jiraProjectKeyField.text.trim() != jiraSettings.jiraProjectKey ||
                jiraIssueTypeField.text.trim() != jiraSettings.jiraIssueType ||
                jiraPriorityField.text.trim() != jiraSettings.jiraPriority
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        validateAndApplyOllamaSettings()
        validateAndApplyPreviewSettings()
        validateAndApplyJiraSettings()
    }

    override fun reset() {
        // Ollama
        apiUrlField.text = ollamaSettings.ollamaApiUrl
        modelField.text = ollamaSettings.modelName
        timeoutField.number = ollamaSettings.requestTimeoutSeconds

        // Preview
        composeColorPreviewCheckBox.isSelected = previewSettings.composeColorPreviewEnabled

        // Jira
        localPropertiesPathField.text = jiraSettings.localPropertiesPath
        jiraUrlField.text = jiraSettings.jiraUrl
        jiraUsernameField.text = jiraSettings.jiraUsername
        jiraApiTokenField.text = jiraSettings.jiraApiToken
        jiraProjectIdField.text = jiraSettings.jiraProjectId
        jiraProjectKeyField.text = jiraSettings.jiraProjectKey
        jiraIssueTypeField.text = jiraSettings.jiraIssueType
        jiraPriorityField.text = jiraSettings.jiraPriority
    }

    override fun disposeUIResources() {
        jiraApiTokenField.text = ""
    }

    override fun getDisplayName(): String = "Lenter"

    private fun createPanel(): JPanel = FormBuilder.createFormBuilder()
        // Ollama Settings
        .addComponent(JBLabel("<html><b>Ollama Settings</b></html>"))
        .addVerticalGap(5)
        .addLabeledComponent("Ollama API URL:", apiUrlField, 1, false)
        .addTooltip("URL Ollama API (например: http://localhost:11434)")
        .addLabeledComponent("Model name:", modelField, 1, false)
        .addTooltip("Название модели (например: qwen2.5-coder:7b, llama3.1:8b)")
        .addLabeledComponent("Timeout (seconds):", timeoutField, 1, false)
        .addTooltip("Максимальное время ожидания ответа ($MIN_TIMEOUT-$MAX_TIMEOUT секунд)")

        .addVerticalGap(20)
        .addComponent(JSeparator())
        .addVerticalGap(15)

        // Preview Settings
        .addComponent(JBLabel("<html><b>Preview</b></html>"))
        .addVerticalGap(5)
        .addComponent(JBLabel("<html><i>Настройки визуального отображения цветов и градиентов</i></html>").apply {
            border = JBUI.Borders.emptyBottom(5)
        })

        .addComponent(JBLabel("<html><b>Compose Color Preview:</b></html>").apply {
            border = JBUI.Borders.emptyTop(5)
        })
        .addComponent(composeColorPreviewCheckBox)
        .addTooltip("Показывать квадраты с цветом для вызовов androidx.compose.ui.graphics.Color")
        .addComponent(showColorInGutterCheckBox)
        .addTooltip("Отображать иконки цвета на левой панели редактора")
        .addLabeledComponent("Размер иконки цвета (px):", colorIconSizeSpinner, 1, false)
        .addTooltip("Размер квадрата с цветом ($MIN_ICON_SIZE-$MAX_ICON_SIZE пикселей)")

        .addVerticalGap(10)
        .addComponent(JBLabel("<html><b>Brush Gradient Preview:</b></html>"))
        .addComponent(brushGradientPreviewCheckBox)
        .addTooltip("Показывать градиенты для Brush.horizontalGradient, Brush.verticalGradient и др.")
        .addLabeledComponent("Размер иконки градиента (px):", gradientIconSizeSpinner, 1, false)
        .addTooltip("Размер иконки с градиентом ($MIN_ICON_SIZE-$MAX_ICON_SIZE пикселей)")

        .addComponent(JBLabel("<html><small><i>Изменения применятся после переоткрытия файлов</i></small></html>").apply {
            border = JBUI.Borders.emptyTop(10)
        })

        .addVerticalGap(20)
        .addComponent(JSeparator())
        .addVerticalGap(15)

        // Jira Settings
        .addComponent(JBLabel("<html><b>Jira Settings</b></html>"))
        .addVerticalGap(5)
        .addComponent(createLocalPropertiesPanel())
        .addVerticalGap(10)
        .addLabeledComponent("Jira URL:", jiraUrlField, 1, false)
        .addTooltip("URL вашего Jira сервера (например: https://jira.company.com)")
        .addLabeledComponent("Username:", jiraUsernameField, 1, false)
        .addTooltip("Ваше имя пользователя в Jira")
        .addLabeledComponent("Personal Access Token:", jiraApiTokenField, 1, false)
        .addComponent(createJiraTokenHelpLink())
        .addComponent(createJiraTokenWarningLabel())
        .addVerticalGap(10)
        .addLabeledComponent("Project ID:", jiraProjectIdField, 1, false)
        .addTooltip("ID проекта в Jira (числовое значение)")
        .addLabeledComponent("Project Key:", jiraProjectKeyField, 1, false)
        .addTooltip("Ключ проекта (например: TBD)")
        .addLabeledComponent("Issue Type ID:", jiraIssueTypeField, 1, false)
        .addTooltip("ID типа задачи (например: 10101 = Bug, 10001 = Task)")
        .addLabeledComponent("Default Priority ID:", jiraPriorityField, 1, false)
        .addTooltip("ID приоритета (1 = Highest, 2 = High, 3 = Medium, 4 = Low, 5 = Lowest)")
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun createLocalPropertiesPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)

        val label = JBLabel("local.properties:")
        label.preferredSize = label.preferredSize.apply { width = DEFAULT_LABEL_WIDTH }

        panel.add(label)
        panel.add(localPropertiesPathField)
        panel.add(Box.createHorizontalStrut(5))
        panel.add(loadFromLocalPropertiesButton)

        return panel
    }

    private fun createJiraTokenHelpLink(): HyperlinkLabel =
        HyperlinkLabel("Создайте Personal Access Token в Jira").apply {
            addHyperlinkListener {
                val baseUrl = jiraUrlField.text.trim().ifEmpty { "https://your-jira-instance.com" }
                val tokenUrl =
                    "$baseUrl/secure/ViewProfile.jspa?selectedTab=com.atlassian.pats.pats-plugin:jira-user-personal-access-tokens"
                setHyperlinkTarget(tokenUrl)
            }
            border = JBUI.Borders.emptyLeft(DEFAULT_LABEL_WIDTH)
        }

    private fun createJiraTokenWarningLabel(): JBLabel = JBLabel(
        "<html><small><b>ВАЖНО:</b> Используйте Personal Access Token!<br>" +
                "Token должен иметь права на создание задач в проекте.</small></html>"
    ).apply {
        border = JBUI.Borders.empty(5, DEFAULT_LABEL_WIDTH, 0, 0)
    }

    @Throws(ConfigurationException::class)
    private fun validateAndApplyOllamaSettings() {
        val timeout = timeoutField.number
        if (timeout !in MIN_TIMEOUT..MAX_TIMEOUT) {
            throw ConfigurationException("Timeout должен быть в диапазоне $MIN_TIMEOUT-$MAX_TIMEOUT секунд")
        }

        val apiUrl = apiUrlField.text.trim()
        if (apiUrl.isBlank()) {
            throw ConfigurationException("Ollama API URL не может быть пустым")
        }

        if (!isValidUrl(apiUrl)) {
            throw ConfigurationException("Ollama API URL имеет неверный формат. Используйте: http://host:port")
        }

        val model = modelField.text.trim()
        if (model.isBlank()) {
            throw ConfigurationException("Model name не может быть пустым")
        }

        ollamaSettings.ollamaApiUrl = apiUrl
        ollamaSettings.modelName = model
        ollamaSettings.requestTimeoutSeconds = timeout
    }

    @Throws(ConfigurationException::class)
    private fun validateAndApplyPreviewSettings() {
        val colorIconSize = colorIconSizeSpinner.number
        if (colorIconSize !in MIN_ICON_SIZE..MAX_ICON_SIZE) {
            throw ConfigurationException("Размер иконки цвета должен быть в диапазоне $MIN_ICON_SIZE-$MAX_ICON_SIZE пикселей")
        }

        val gradientIconSize = gradientIconSizeSpinner.number
        if (gradientIconSize !in MIN_ICON_SIZE..MAX_ICON_SIZE) {
            throw ConfigurationException("Размер иконки градиента должен быть в диапазоне $MIN_ICON_SIZE-$MAX_ICON_SIZE пикселей")
        }

        previewSettings.composeColorPreviewEnabled = composeColorPreviewCheckBox.isSelected
    }

    @Throws(ConfigurationException::class)
    private fun validateAndApplyJiraSettings() {
        val jiraUrl = jiraUrlField.text.trim()
        val jiraToken = String(jiraApiTokenField.password).trim()

        if (jiraUrl.isNotBlank()) {
            if (!isValidUrl(jiraUrl)) {
                throw ConfigurationException("Jira URL имеет неверный формат. Используйте: https://jira.company.com")
            }

            if (jiraToken.isBlank()) {
                throw ConfigurationException("Укажите Personal Access Token для работы с Jira")
            }
        }

        jiraSettings.apply {
            this.localPropertiesPath = localPropertiesPathField.text.trim()
            this.jiraUrl = jiraUrl.trimEnd('/')
            this.jiraUsername = jiraUsernameField.text.trim()
            this.jiraApiToken = jiraToken
            this.jiraProjectId = jiraProjectIdField.text.trim()
            this.jiraProjectKey = jiraProjectKeyField.text.trim()
            this.jiraIssueType = jiraIssueTypeField.text.trim()
            this.jiraPriority = jiraPriorityField.text.trim()
        }
    }

    private fun isValidUrl(urlString: String): Boolean {
        return runCatching {
            val url = URL(urlString)
            url.protocol in setOf("http", "https")
        }.getOrDefault(false)
    }

    private fun loadJiraSettingsFromLocalProperties() {
        val filePath = localPropertiesPathField.text.trim()

        if (filePath.isBlank()) {
            Messages.showWarningDialog(
                panel,
                "Выберите файл local.properties",
                "Файл не выбран"
            )
            return
        }

        val defaults = jiraSettings.readLocalProperties(filePath)

        if (defaults == null) {
            Messages.showWarningDialog(
                panel,
                "Файл '$filePath' не найден или не содержит Jira настройки.\n\n" +
                        "Убедитесь, что файл содержит свойства:\n" +
                        "jira.url=https://your-jira.com\n" +
                        "jira.username=your-email\n" +
                        "jira.apiToken=your-token\n" +
                        "jira.projectId=12345\n" +
                        "jira.projectKey=PROJ\n" +
                        "jira.issueType=10001\n" +
                        "jira.priority=3",
                "Не удалось загрузить настройки"
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
            "Настройки Jira успешно загружены из:\n${defaults.origin}",
            "Настройки обновлены"
        )
    }
}
