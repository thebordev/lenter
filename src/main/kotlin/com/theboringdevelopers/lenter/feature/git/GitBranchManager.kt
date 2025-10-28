package com.theboringdevelopers.lenter.feature.git

import com.intellij.openapi.project.Project
import java.io.File

/**
 * Менеджер для работы с Git без зависимостей от плагинов
 */
object GitBranchManager {

    /**
     * Получает имя текущей ветки Git
     *
     * @param project проект IntelliJ
     * @return имя текущей ветки или null если не удалось определить
     */
    fun getCurrentBranchName(project: Project): String? {
        val basePath = project.basePath ?: return null

        return readBranchFromGitHead(basePath)
            ?: readBranchFromGitCommand(basePath)
    }

    private fun readBranchFromGitHead(projectPath: String): String? {
        return try {
            val gitHeadFile = File(projectPath, ".git/HEAD")

            if (!gitHeadFile.exists()) {
                return null
            }

            val headContent = gitHeadFile.readText().trim()

            if (headContent.startsWith("ref: refs/heads/")) {
                headContent.removePrefix("ref: refs/heads/")
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error reading .git/HEAD: ${e.message}")
            null
        }
    }

    /**
     * Получает имя ветки через команду git
     */
    private fun readBranchFromGitCommand(projectPath: String): String? {
        return try {
            val processBuilder = ProcessBuilder(
                "git",
                "rev-parse",
                "--abbrev-ref",
                "HEAD"
            )
            processBuilder.directory(File(projectPath))
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()

            process.waitFor()

            if (process.exitValue() == 0 && output.isNotEmpty() && output != "HEAD") {
                output
            } else {
                null
            }
        } catch (e: Exception) {
            println("Error executing git command: ${e.message}")
            null
        }
    }

    /**
     * Извлекает номер задачи Jira из названия ветки
     *
     * @param branchName название ветки
     */
    fun extractIssueKey(branchName: String): String? {
        val pattern = Regex("([A-Z]+)-([0-9]+)", RegexOption.IGNORE_CASE)
        val match = pattern.find(branchName)
        return match?.value?.uppercase()
    }

    /**
     * Проверяет, является ли директория Git репозиторием
     */
    fun isGitRepository(projectPath: String): Boolean {
        val gitDir = File(projectPath, ".git")
        return gitDir.exists() && (gitDir.isDirectory || gitDir.isFile)
    }

    /**
     * Получает информацию о текущей ветке и задаче Jira
     */
    fun getBranchInfo(project: Project): BranchInfo? {
        val basePath = project.basePath ?: return null

        if (!isGitRepository(basePath)) {
            return null
        }

        val branchName = getCurrentBranchName(project) ?: return null
        val issueKey = extractIssueKey(branchName)

        return BranchInfo(
            branchName = branchName,
            issueKey = issueKey
        )
    }

    /**
     * Информация о текущей ветке
     */
    data class BranchInfo(
        val branchName: String,
        val issueKey: String?
    )
}
