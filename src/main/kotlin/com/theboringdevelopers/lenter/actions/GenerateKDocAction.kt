package com.theboringdevelopers.lenter.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.theboringdevelopers.lenter.feature.commentator.KDocGenerator
import com.theboringdevelopers.lenter.ollama.OllamaClient
import com.theboringdevelopers.lenter.settings.CommentatorSettingsState
import org.jetbrains.kotlin.psi.*

class GenerateKDocForClassAction : AnAction(), DumbAware {

    private val commentGenerator = KDocGenerator()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val ktFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return

        val doc = editor.document
        val psiMgr = PsiDocumentManager.getInstance(project)
        psiMgr.commitDocument(doc)

        val caretOffset = editor.caretModel.offset
        val element = ktFile.findElementAt(caretOffset)

        val ktClass = PsiTreeUtil.getParentOfType(element, KtClass::class.java)
            ?: PsiTreeUtil.getParentOfType(element, KtObjectDeclaration::class.java)

        if (ktClass == null) {
            Messages.showWarningDialog(
                project,
                "Курсор должен находиться внутри класса или object",
                "Lenter"
            )
            return
        }

        val declarations = mutableListOf<KtDeclaration>()

        declarations.add(ktClass)

        ktClass.declarations.forEach { decl ->
            when (decl) {
                is KtNamedFunction -> declarations.add(decl)
                is KtProperty -> declarations.add(decl)
                is KtClass -> declarations.add(decl)
                is KtObjectDeclaration -> declarations.add(decl)
            }
        }

        val ans = Messages.showYesNoDialog(
            project,
            "Будет создано ${declarations.size} комментариев для класса ${ktClass.name}.\nПродолжить?",
            "Lenter",
            Messages.getQuestionIcon()
        )
        if (ans != Messages.YES) return

        val client = OllamaClient(CommentatorSettingsState.getInstance())

        object : Task.Backgroundable(project, "Generating Comments for Class...", true) {
            private val results = mutableMapOf<KtDeclaration, String>()
            private var processed = 0

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                for ((index, decl) in declarations.withIndex()) {
                    if (indicator.isCanceled) break

                    indicator.fraction = index.toDouble() / declarations.size
                    indicator.text = "Generating comment ${index + 1}/${declarations.size}..."

                    val code = decl.text
                    val isProperty = commentGenerator.isClassProperty(decl)
                    val hasReturnValue = commentGenerator.hasReturnValue(decl)
                    val prompt = commentGenerator.buildPrompt(code, isProperty, hasReturnValue)

                    when (val result = client.generate(prompt)) {
                        is OllamaClient.Result.Success -> {
                            val sanitized = commentGenerator.validateAndFixComment(
                                text = commentGenerator.sanitizeResponse(
                                    raw = result.text,
                                    isProperty = isProperty,
                                    hasReturnValue = hasReturnValue,
                                    decl = decl
                                ),
                                isProperty = isProperty,
                                hasReturnValue = hasReturnValue
                            )
                            results[decl] = sanitized
                            processed++
                        }

                        is OllamaClient.Result.Error -> {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    "Ошибка при генерации комментария для ${decl.name}: ${result.message}",
                                    "Lenter"
                                )
                            }
                            return
                        }
                    }
                }

                if (results.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        insertComments(project, doc, results)
                        Messages.showInfoMessage(
                            project,
                            "Успешно создано $processed комментариев",
                            "Lenter"
                        )
                    }
                }
            }

            private fun insertComments(
                project: com.intellij.openapi.project.Project,
                doc: com.intellij.openapi.editor.Document,
                comments: Map<KtDeclaration, String>
            ) {
                WriteCommandAction.runWriteCommandAction(project) {
                    comments.entries
                        .sortedByDescending { it.key.textRange.startOffset }
                        .forEach { (decl, comment) ->
                            if (comment.isBlank()) return@forEach

                            decl.docComment?.let { dc ->
                                doc.deleteString(dc.textRange.startOffset, dc.textRange.endOffset)
                            }

                            val declStart = decl.textRange.startOffset
                            val declLine = doc.getLineNumber(declStart)
                            val declLineStart = doc.getLineStartOffset(declLine)
                            val indent = doc.getText(
                                com.intellij.openapi.util.TextRange(declLineStart, declStart)
                            ).takeWhile { it == ' ' || it == '\t' }

                            val formatted = commentGenerator.formatComment(comment, indent) + "\n"
                            doc.insertString(declLineStart, formatted)
                        }
                }
            }
        }.queue()
    }
}