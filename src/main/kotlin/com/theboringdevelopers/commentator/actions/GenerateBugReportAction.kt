package com.theboringdevelopers.commentator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.theboringdevelopers.commentator.feature.reporter.BugReportDialog

class GenerateBugReportAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dialog = BugReportDialog(project)
        dialog.show()
    }
}
