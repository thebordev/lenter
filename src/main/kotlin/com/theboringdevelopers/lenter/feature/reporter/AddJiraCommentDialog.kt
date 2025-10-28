package com.theboringdevelopers.lenter.feature.reporter

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.theboringdevelopers.lenter.feature.jira.JiraCommentClient
import com.theboringdevelopers.lenter.settings.JiraSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel

class AddJiraCommentDialog(
    private val project: Project,
    private val issueKey: String,
    private val branchName: String = ""
) : DialogWrapper(project) {

    private val commentArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 10
        emptyText.text = "Введите комментарий для задачи $issueKey..."
    }

    private val statusLabel = JBLabel(" ").apply {
        border = JBUI.Borders.empty(5)
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        title = "Добавить комментарий в Jira"
        setOKButtonText("Отправить")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 10))
        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(600, 350)

        val headerPanel = JPanel(BorderLayout(0, 5))

        val taskLabel = JBLabel("<html><b>Задача:</b> $issueKey</html>")
        headerPanel.add(taskLabel, BorderLayout.NORTH)

        if (branchName.isNotEmpty()) {
            val branchLabel = JBLabel("<html><small>Ветка: $branchName</small></html>")
            branchLabel.foreground = Color.GRAY
            headerPanel.add(branchLabel, BorderLayout.CENTER)
        }

        val jiraUrl = JiraSettingsState.getInstance().jiraUrl
        if (jiraUrl.isNotEmpty()) {
            val linkPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
            val linkLabel = JBLabel("<html><a href=''>Открыть в Jira →</a></html>")
            linkLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    BrowserUtil.browse("$jiraUrl/browse/$issueKey")
                }
            })
            linkLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            linkPanel.add(linkLabel)
            headerPanel.add(linkPanel, BorderLayout.SOUTH)
        }

        panel.add(headerPanel, BorderLayout.NORTH)

        val textPanel = JPanel(BorderLayout(0, 5))
        textPanel.add(JBLabel("Комментарий:"), BorderLayout.NORTH)
        textPanel.add(JBScrollPane(commentArea), BorderLayout.CENTER)

        panel.add(textPanel, BorderLayout.CENTER)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    override fun doOKAction() {
        val commentText = commentArea.text.trim()

        if (commentText.isEmpty()) {
            JOptionPane.showMessageDialog(
                contentPane,
                "Введите текст комментария",
                "Ошибка",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        isOKActionEnabled = false
        statusLabel.text = "Отправка комментария..."

        scope.launch {
            try {
                val jiraSettings = JiraSettingsState.getInstance()

                if (jiraSettings.jiraUrl.isBlank()) {
                    statusLabel.text = "Ошибка: Jira не настроен"
                    isOKActionEnabled = true

                    JOptionPane.showMessageDialog(
                        contentPane,
                        "Настройте Jira в Settings → Tools → Commentator",
                        "Ошибка",
                        JOptionPane.WARNING_MESSAGE
                    )
                    return@launch
                }

                val client = JiraCommentClient(jiraSettings)

                val result = withContext(Dispatchers.IO) {
                    client.addComment(issueKey, commentText)
                }

                when (result) {
                    is JiraCommentClient.Result.Success -> {
                        statusLabel.text = "Комментарий добавлен ✓"

                        val response = JOptionPane.showConfirmDialog(
                            contentPane,
                            "Комментарий успешно добавлен к задаче $issueKey\n\nОткрыть в браузере?",
                            "Успех",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE
                        )

                        if (response == JOptionPane.YES_OPTION) {
                            BrowserUtil.browse(result.commentUrl)
                        }

                        super.doOKAction()
                    }

                    is JiraCommentClient.Result.Error -> {
                        statusLabel.text = "Ошибка: HTTP ${result.statusCode}"
                        isOKActionEnabled = true

                        JOptionPane.showMessageDialog(
                            contentPane,
                            result.message,
                            "Ошибка добавления комментария",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            } catch (e: Exception) {
                statusLabel.text = "Ошибка: ${e.message}"
                isOKActionEnabled = true

                JOptionPane.showMessageDialog(
                    contentPane,
                    "Ошибка: ${e.message}",
                    "Ошибка",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }
}