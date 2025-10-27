package com.theboringdevelopers.commentator.feature.reporter

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.theboringdevelopers.commentator.feature.jira.JiraClient
import com.theboringdevelopers.commentator.ollama.OllamaClient
import com.theboringdevelopers.commentator.settings.CommentatorSettingsState
import com.theboringdevelopers.commentator.settings.JiraSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*

class BugReportDialog(private val project: Project) : DialogWrapper(project) {

    private val inputArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 8
        emptyText.text = "Опишите баг свободным текстом..."
    }

    private val previewPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
    }

    // Редактируемые поля
    private val summaryField = JBTextField().apply {
        emptyText.text = "Краткое описание задачи"
    }

    private val environmentField = JBTextField().apply {
        emptyText.text = "prod/stage/dev"
    }

    private val buildNumberField = JBTextField().apply {
        emptyText.text = "0.30.0.1590"
    }

    private val phoneModelField = JBTextField().apply {
        text = "Any"
    }

    private val osVersionField = JBTextField().apply {
        text = "Any"
    }

    private val carField = JBTextField().apply {
        emptyText.text = "Модель автомобиля"
    }

    private val requirementLinkField = JBTextField().apply {
        emptyText.text = "Ссылка на требование"
    }

    private val preconditionsArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        emptyText.text = "Предусловия для воспроизведения"
    }

    private val stepsArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 5
        emptyText.text = "Шаг 1\nШаг 2\nШаг 3"
    }

    private val actualResultArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        emptyText.text = "Что произошло на самом деле"
    }

    private val expectedResultArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        emptyText.text = "Что должно было произойти"
    }

    private val additionalInfoArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 3
        emptyText.text = "Дополнительная информация"
    }

    private val generateButton = JButton("Сгенерировать").apply {
        addActionListener { generateReport() }
    }

    private val updatePreviewButton = JButton("Обновить предпросмотр").apply {
        isEnabled = false
        addActionListener { updatePreview() }
    }

    private val copyButton = JButton("Копировать код").apply {
        isEnabled = false
        addActionListener { copyToClipboard() }
    }

    private val createJiraButton = JButton("Создать задачу в Jira").apply {
        isEnabled = false
        addActionListener { createJiraIssue() }
    }

    private val statusLabel = JLabel(" ").apply {
        border = JBUI.Borders.empty(5)
    }

    private val bugReportGenerator = BugReportGenerator()
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        title = "Генератор Bug Report"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 10))
        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(1100, 800)

        // Верхняя панель с вводом
        val inputPanel = JPanel(BorderLayout(0, 5))
        inputPanel.add(JLabel("Описание бага:"), BorderLayout.NORTH)
        inputPanel.add(JBScrollPane(inputArea), BorderLayout.CENTER)
        inputPanel.add(generateButton, BorderLayout.SOUTH)

        // Нижняя панель - вкладки
        val resultPanel = JPanel(BorderLayout(0, 5))
        val tabbedPane = JTabbedPane()

        // Tab 1: Preview
        val previewPanel = JPanel(BorderLayout())
        previewPanel.add(JBScrollPane(previewPane), BorderLayout.CENTER)
        tabbedPane.addTab("📊 Предпросмотр", previewPanel)

        // Tab 2: Edit
        val editPanel = createEditPanel()
        tabbedPane.addTab("✏️ Редактировать", editPanel)

        // Кнопки
        val buttonPanel = JPanel()
        buttonPanel.add(updatePreviewButton)
        buttonPanel.add(copyButton)
        buttonPanel.add(createJiraButton)

        resultPanel.add(tabbedPane, BorderLayout.CENTER)
        resultPanel.add(buttonPanel, BorderLayout.SOUTH)

        // Главный разделитель
        val splitPane = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            inputPanel,
            resultPanel
        ).apply {
            resizeWeight = 0.2
            dividerLocation = 150
        }

        panel.add(splitPane, BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createEditPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val formPanel = FormBuilder.createFormBuilder()
            .addComponent(JLabel("<html><b>Основная информация</b></html>"))
            .addVerticalGap(5)
            .addLabeledComponent("Название задачи:", summaryField, 1, false)
            .addVerticalGap(10)

            .addComponent(JSeparator())
            .addVerticalGap(10)
            .addComponent(JLabel("<html><b>Технические параметры</b></html>"))
            .addVerticalGap(5)
            .addLabeledComponent("Окружение:", environmentField, 1, false)
            .addLabeledComponent("Номер сборки:", buildNumberField, 1, false)
            .addLabeledComponent("Телефон (модель):", phoneModelField, 1, false)
            .addLabeledComponent("Версия ОС:", osVersionField, 1, false)
            .addLabeledComponent("Автомобиль:", carField, 1, false)
            .addLabeledComponent("Ссылка на требование:", requirementLinkField, 1, false)
            .addVerticalGap(10)

            .addComponent(JSeparator())
            .addVerticalGap(10)
            .addComponent(JLabel("<html><b>Описание бага</b></html>"))
            .addVerticalGap(5)
            .addLabeledComponent("Предусловия:", JBScrollPane(preconditionsArea), 1, false)
            .addLabeledComponent("Шаги воспроизведения:", JBScrollPane(stepsArea), 1, false)
            .addLabeledComponent("Фактический результат:", JBScrollPane(actualResultArea), 1, false)
            .addLabeledComponent("Ожидаемый результат:", JBScrollPane(expectedResultArea), 1, false)
            .addLabeledComponent("Дополнительная информация:", JBScrollPane(additionalInfoArea), 1, false)

            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel.add(JBScrollPane(formPanel), BorderLayout.CENTER)

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("Настройки Jira") {
                override fun doAction(e: ActionEvent?) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Commentator")
                }
            },
            cancelAction
        )
    }

    private fun generateReport() {
        val description = inputArea.text.trim()
        if (description.isEmpty()) {
            previewPane.text = "<html><body><p>Введите описание бага</p></body></html>"
            return
        }

        generateButton.isEnabled = false
        previewPane.text = "<html><body><p>Генерация отчета...</p></body></html>"
        statusLabel.text = "Генерация..."

        scope.launch {
            try {
                val client = OllamaClient(CommentatorSettingsState.getInstance())
                val prompt = bugReportGenerator.buildPrompt(description)

                val result = withContext(Dispatchers.IO) {
                    client.generate(prompt)
                }

                when (result) {
                    is OllamaClient.Result.Success -> {
                        val bugReport = bugReportGenerator.parseBugReport(result.text)
                        if (bugReport != null) {
                            // Заполняем поля
                            fillFields(bugReport, description)

                            // Обновляем preview
                            updatePreview()

                            updatePreviewButton.isEnabled = true
                            copyButton.isEnabled = true
                            createJiraButton.isEnabled = true
                            statusLabel.text = "Готово ✓ Отредактируйте поля и нажмите 'Обновить предпросмотр'"
                        } else {
                            showParsingError(result.text)
                        }
                    }

                    is OllamaClient.Result.Error -> {
                        previewPane.contentType = "text/plain"
                        previewPane.text = "Ошибка: ${result.message}"
                        statusLabel.text = "Ошибка: ${result.message}"
                    }
                }
            } catch (e: Exception) {
                previewPane.contentType = "text/plain"
                previewPane.text = "Ошибка: ${e.message}"
                statusLabel.text = "Ошибка: ${e.message}"
            } finally {
                generateButton.isEnabled = true
            }
        }
    }

    private fun fillFields(bugReport: BugReport, originalDescription: String) {
        summaryField.text = extractSummary(originalDescription, bugReport)
        environmentField.text = bugReport.environment
        buildNumberField.text = bugReport.buildNumber
        phoneModelField.text = bugReport.phoneModel
        osVersionField.text = bugReport.osVersion
        carField.text = bugReport.car
        requirementLinkField.text = bugReport.requirementLink
        preconditionsArea.text = bugReport.preconditions
        stepsArea.text = bugReport.stepsToReproduce
        actualResultArea.text = bugReport.actualResult
        expectedResultArea.text = bugReport.expectedResult
        additionalInfoArea.text = bugReport.additionalInfo
    }

    private fun getCurrentBugReport(): BugReport {
        return BugReport(
            environment = environmentField.text.trim(),
            buildNumber = buildNumberField.text.trim(),
            phoneModel = phoneModelField.text.trim(),
            osVersion = osVersionField.text.trim(),
            car = carField.text.trim(),
            requirementLink = requirementLinkField.text.trim(),
            preconditions = preconditionsArea.text.trim(),
            stepsToReproduce = stepsArea.text.trim(),
            actualResult = actualResultArea.text.trim(),
            expectedResult = expectedResultArea.text.trim(),
            additionalInfo = additionalInfoArea.text.trim()
        )
    }

    private fun updatePreview() {
        val bugReport = getCurrentBugReport()

        // Обновляем HTML preview
        previewPane.contentType = "text/html"
        previewPane.text = bugReport.toHtmlTable()
        previewPane.caretPosition = 0

        statusLabel.text = "Предпросмотр обновлен ✓"
    }

    private fun showParsingError(rawResponse: String) {
        previewPane.contentType = "text/html"
        previewPane.text = """
            <html>
            <body>
                <h3 style="color: red;">Ошибка парсинга JSON</h3>
                <p>Модель вернула невалидный JSON. Попробуйте:</p>
                <ul>
                    <li>Переформулировать описание бага</li>
                    <li>Использовать модель qwen2.5-coder:7b вместо llama3.1:8b</li>
                </ul>
                <h4>Сырой ответ модели:</h4>
                <pre style="background: #f0f0f0; padding: 10px; overflow: auto;">${
            rawResponse.replace("<", "&lt;").replace(">", "&gt;")
        }</pre>
            </body>
            </html>
        """.trimIndent()
        statusLabel.text = "Ошибка парсинга JSON"
    }

    private fun extractSummary(description: String, bugReport: BugReport): String {
        val lines = description.lines()
        val firstMeaningfulLine = lines.firstOrNull { it.length > 10 && !it.startsWith("В") }

        return when {
            firstMeaningfulLine != null && firstMeaningfulLine.length < 100 -> firstMeaningfulLine
            bugReport.actualResult.isNotEmpty() -> bugReport.actualResult.take(100)
            else -> "Bug: ${description.take(50)}..."
        }
    }

    private fun createJiraIssue() {
        val summary = summaryField.text.trim()

        if (summary.isEmpty()) {
            JOptionPane.showMessageDialog(
                contentPane,
                "Введите название задачи (Summary)",
                "Ошибка",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val bugReport = getCurrentBugReport()
        val description = bugReport.toJiraTable()

        createJiraButton.isEnabled = false
        statusLabel.text = "Создание задачи в Jira..."

        scope.launch {
            try {
                val jiraSettings = JiraSettingsState.getInstance()
                val jiraClient = JiraClient(jiraSettings)

                val result = withContext(Dispatchers.IO) {
                    jiraClient.createIssue(
                        summary = summary,
                        description = description
                    )
                }

                when (result) {
                    is JiraClient.Result.Success -> {
                        statusLabel.text = "Задача создана: ${result.issueKey}"

                        val response = JOptionPane.showConfirmDialog(
                            contentPane,
                            "Задача ${result.issueKey} успешно создана!\n\nОткрыть в браузере?",
                            "Успех",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE
                        )

                        if (response == JOptionPane.YES_OPTION) {
                            BrowserUtil.browse(result.issueUrl)
                        }

                        // Очищаем форму после успешного создания
                        clearForm()
                    }

                    is JiraClient.Result.Error -> {
                        statusLabel.text = "Ошибка: HTTP ${result.statusCode}"

                        val message = if (result.statusCode == 401) {
                            """
                            ${result.message}
                            
                            Откройте настройки и пересоздайте Personal Access Token.
                            """.trimIndent()
                        } else {
                            result.message
                        }

                        JOptionPane.showMessageDialog(
                            contentPane,
                            message,
                            "Ошибка создания задачи",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            } catch (e: Exception) {
                statusLabel.text = "Ошибка: ${e.message}"
                JOptionPane.showMessageDialog(
                    contentPane,
                    "Ошибка: ${e.message}",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE
                )
            } finally {
                createJiraButton.isEnabled = true
            }
        }
    }

    private fun copyToClipboard() {
        val bugReport = getCurrentBugReport()
        val text = bugReport.toJiraTable()

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)

        copyButton.text = "Скопировано ✓"
        statusLabel.text = "Jira код скопирован в буфер обмена"
        Timer(2000) {
            copyButton.text = "Копировать код"
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun clearForm() {
        inputArea.text = ""
        summaryField.text = ""
        environmentField.text = ""
        buildNumberField.text = ""
        phoneModelField.text = "Any"
        osVersionField.text = "Any"
        carField.text = ""
        requirementLinkField.text = ""
        preconditionsArea.text = ""
        stepsArea.text = ""
        actualResultArea.text = ""
        expectedResultArea.text = ""
        additionalInfoArea.text = ""
        previewPane.text = ""

        updatePreviewButton.isEnabled = false
        copyButton.isEnabled = false
        createJiraButton.isEnabled = false
    }
}