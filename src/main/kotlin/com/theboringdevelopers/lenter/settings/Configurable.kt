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

/**
 * Страница настроек плагина Lenter
 */
class Configurable : Configurable {

    private companion object {
        const val MIN_TIMEOUT = 10
        const val MAX_TIMEOUT = 600
        const val DEFAULT_LABEL_WIDTH = 180
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
    private val drawablePreviewInCodeCheckBox = JBCheckBox("Показывать preview drawable в коде (inline)")
    private val drawablePreviewInTreeCheckBox = JBCheckBox("Показывать preview drawable в дереве файлов")

    // String Resource Preview
    private val stringResourcePreviewCheckBox = JBCheckBox("Показывать preview строковых ресурсов")
    private val stringLanguageComboBox = JComboBox<LanguageOption>().apply {
        addItem(LanguageOption("default", "По умолчанию (values)"))
        addItem(LanguageOption("ru", "Русский (values-ru)"))
        addItem(LanguageOption("en", "Английский (values-en)"))
        addItem(LanguageOption("de", "Немецкий (values-de)"))
        addItem(LanguageOption("fr", "Французский (values-fr)"))
        addItem(LanguageOption("es", "Испанский (values-es)"))
        addItem(LanguageOption("it", "Итальянский (values-it)"))
        addItem(LanguageOption("pt", "Португальский (values-pt)"))
        addItem(LanguageOption("ja", "Японский (values-ja)"))
        addItem(LanguageOption("zh", "Китайский (values-zh)"))
    }

    private data class LanguageOption(
        val code: String,
        val displayName: String,
    ) {
        override fun toString() = displayName
    }

    // Jira Settings
    private val localPropertiesPathField = TextFieldWithBrowseButton().apply {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withTitle("Выберите local.properties")
            .withDescription("Выберите файл local.properties с настройками Jira")
            .withFileFilter { it.name == "local.properties" || it.extension.equals("properties", true) }

        addBrowseFolderListener(
            "Выберите local.properties",
            "Выберите файл local.properties с настройками Jira",
            null,
            descriptor
        )
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
        setupListeners()
        return panel
    }

    override fun isModified(): Boolean {
        return isOllamaSettingsModified() ||
                isPreviewSettingsModified() ||
                isJiraSettingsModified()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        validateAndApplyOllamaSettings()
        validateAndApplyPreviewSettings()
        validateAndApplyJiraSettings()
    }

    override fun reset() {
        resetOllamaSettings()
        resetPreviewSettings()
        resetJiraSettings()
    }

    override fun disposeUIResources() {
        jiraApiTokenField.text = ""
    }

    override fun getDisplayName(): String = "Lenter"

    // ==================== UI Creation ====================

    private fun createPanel(): JPanel = FormBuilder.createFormBuilder()
        .addOllamaSettingsSection()
        .addSeparator()
        .addPreviewSettingsSection()
        .addSeparator()
        .addJiraSettingsSection()
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun FormBuilder.addOllamaSettingsSection(): FormBuilder = this
        .addComponent(JBLabel("<html><b>Ollama Settings</b></html>"))
        .addVerticalGap(5)
        .addLabeledComponent("Ollama API URL:", apiUrlField, 1, false)
        .addTooltip("URL Ollama API (например: http://localhost:11434)")
        .addLabeledComponent("Model name:", modelField, 1, false)
        .addTooltip("Название модели (например: qwen2.5-coder:7b, llama3.1:8b)")
        .addLabeledComponent("Timeout (seconds):", timeoutField, 1, false)
        .addTooltip("Максимальное время ожидания ответа ($MIN_TIMEOUT-$MAX_TIMEOUT секунд)")

    private fun FormBuilder.addPreviewSettingsSection(): FormBuilder = this
        .addComponent(JBLabel("<html><b>Preview Settings</b></html>"))
        .addVerticalGap(5)
        .addComponent(JBLabel("<html><i>Включение/выключение визуального предпросмотра</i></html>").apply {
            border = JBUI.Borders.emptyBottom(5)
        })
        .addComponent(composeColorPreviewCheckBox)
        .addTooltip("Показывать квадраты с цветом для androidx.compose.ui.graphics.Color")

        .addVerticalGap(10)
        .addComponent(JBLabel("<html><b>Drawable Preview:</b></html>"))
        .addComponent(drawablePreviewInCodeCheckBox)
        .addTooltip("Показывать preview drawable рядом с painterResource() и vectorResource() в коде")
        .addComponent(drawablePreviewInTreeCheckBox)
        .addTooltip("Показывать миниатюры drawable файлов в дереве проекта")

        .addVerticalGap(10)
        .addComponent(JBLabel("<html><b>String Resource Preview:</b></html>"))
        .addComponent(stringResourcePreviewCheckBox)
        .addTooltip("Показывать содержимое строковых ресурсов inline в коде")
        .addLabeledComponent("Приоритетный язык:", stringLanguageComboBox, 1, false)
        .addTooltip("Язык для отображения строк (используется fallback на default при отсутствии)")

        .addComponent(JBLabel("<html><small><i>Изменения применятся после переоткрытия файлов</i></small></html>").apply {
            border = JBUI.Borders.emptyTop(10)
        })

    private fun FormBuilder.addJiraSettingsSection(): FormBuilder = this
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

    private fun setupListeners() {
        stringResourcePreviewCheckBox.addActionListener {
            stringLanguageComboBox.isEnabled = stringResourcePreviewCheckBox.isSelected
        }
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
        val selectedLanguage = (stringLanguageComboBox.selectedItem as? LanguageOption)?.code ?: "default"

        previewSettings.apply {
            composeColorPreviewEnabled = composeColorPreviewCheckBox.isSelected
            drawablePreviewInCodeEnabled = drawablePreviewInCodeCheckBox.isSelected
            drawablePreviewInTreeEnabled = drawablePreviewInTreeCheckBox.isSelected

            stringResourcePreviewEnabled = stringResourcePreviewCheckBox.isSelected
            stringResourcePreferredLanguage = selectedLanguage
        }
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
            localPropertiesPath = localPropertiesPathField.text.trim()
            this.jiraUrl = jiraUrl.trimEnd('/')
            jiraUsername = jiraUsernameField.text.trim()
            jiraApiToken = jiraToken
            jiraProjectId = jiraProjectIdField.text.trim()
            jiraProjectKey = jiraProjectKeyField.text.trim()
            jiraIssueType = jiraIssueTypeField.text.trim()
            jiraPriority = jiraPriorityField.text.trim()
        }
    }

    // ==================== Reset ====================

    private fun resetOllamaSettings() {
        apiUrlField.text = ollamaSettings.ollamaApiUrl
        modelField.text = ollamaSettings.modelName
        timeoutField.number = ollamaSettings.requestTimeoutSeconds
    }

    private fun resetPreviewSettings() {
        composeColorPreviewCheckBox.isSelected = previewSettings.composeColorPreviewEnabled
        drawablePreviewInCodeCheckBox.isSelected = previewSettings.drawablePreviewInCodeEnabled
        drawablePreviewInTreeCheckBox.isSelected = previewSettings.drawablePreviewInTreeEnabled

        stringResourcePreviewCheckBox.isSelected = previewSettings.stringResourcePreviewEnabled

        // Установить выбранный язык
        val languageCode = previewSettings.stringResourcePreferredLanguage
        for (i in 0 until stringLanguageComboBox.itemCount) {
            val item = stringLanguageComboBox.getItemAt(i)
            if (item.code == languageCode) {
                stringLanguageComboBox.selectedIndex = i
                break
            }
        }

        // Обновить enabled состояние
        stringLanguageComboBox.isEnabled = stringResourcePreviewCheckBox.isSelected
    }

    private fun resetJiraSettings() {
        localPropertiesPathField.text = jiraSettings.localPropertiesPath
        jiraUrlField.text = jiraSettings.jiraUrl
        jiraUsernameField.text = jiraSettings.jiraUsername
        jiraApiTokenField.text = jiraSettings.jiraApiToken
        jiraProjectIdField.text = jiraSettings.jiraProjectId
        jiraProjectKeyField.text = jiraSettings.jiraProjectKey
        jiraIssueTypeField.text = jiraSettings.jiraIssueType
        jiraPriorityField.text = jiraSettings.jiraPriority
    }

    private fun isOllamaSettingsModified(): Boolean {
        return apiUrlField.text.trim() != ollamaSettings.ollamaApiUrl ||
                modelField.text.trim() != ollamaSettings.modelName ||
                timeoutField.number != ollamaSettings.requestTimeoutSeconds
    }

    private fun isPreviewSettingsModified(): Boolean {
        val selectedLanguage = (stringLanguageComboBox.selectedItem as? LanguageOption)?.code ?: "default"

        return composeColorPreviewCheckBox.isSelected != previewSettings.composeColorPreviewEnabled ||
                drawablePreviewInCodeCheckBox.isSelected != previewSettings.drawablePreviewInCodeEnabled ||
                drawablePreviewInTreeCheckBox.isSelected != previewSettings.drawablePreviewInTreeEnabled ||
                stringResourcePreviewCheckBox.isSelected != previewSettings.stringResourcePreviewEnabled ||
                selectedLanguage != previewSettings.stringResourcePreferredLanguage
    }

    private fun isJiraSettingsModified(): Boolean {
        return localPropertiesPathField.text.trim() != jiraSettings.localPropertiesPath ||
                jiraUrlField.text.trim() != jiraSettings.jiraUrl ||
                jiraUsernameField.text.trim() != jiraSettings.jiraUsername ||
                !jiraApiTokenField.password.contentEquals(jiraSettings.jiraApiToken.toCharArray()) ||
                jiraProjectIdField.text.trim() != jiraSettings.jiraProjectId ||
                jiraProjectKeyField.text.trim() != jiraSettings.jiraProjectKey ||
                jiraIssueTypeField.text.trim() != jiraSettings.jiraIssueType ||
                jiraPriorityField.text.trim() != jiraSettings.jiraPriority
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
