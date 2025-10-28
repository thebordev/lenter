package com.theboringdevelopers.lenter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.theboringdevelopers.lenter.feature.git.GitBranchManager
import com.theboringdevelopers.lenter.feature.reporter.AddJiraCommentDialog

class AddJiraCommentAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            val branchInfo = GitBranchManager.getBranchInfo(project)

            if (branchInfo == null) {
                Messages.showWarningDialog(
                    project,
                    "Git репозиторий не найден или не удалось определить текущую ветку",
                    "Lenter"
                )
                return
            }

            val issueKey = branchInfo.issueKey

            if (issueKey == null) {
                Messages.showWarningDialog(
                    project,
                    "Не удалось извлечь номер задачи из ветки: ${branchInfo.branchName}\n\n" +
                            "Ожидается формат: feature/PROJECT-123, bugfix/ABC-456, и т.д.",
                    "Lenter"
                )
                return
            }

            val dialog = AddJiraCommentDialog(project, issueKey, branchInfo.branchName)
            dialog.show()

        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Ошибка: ${e.message}",
                "Lenter"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val p = e.presentation
        val project = e.project
        val isGit = project?.basePath?.let { GitBranchManager.isGitRepository(it) } ?: false
        p.isEnabled = project != null
        if (!isGit) p.description = "Requires a Git repository in this project"
        else p.description = "Add comment to Jira from current Git branch"
    }
}