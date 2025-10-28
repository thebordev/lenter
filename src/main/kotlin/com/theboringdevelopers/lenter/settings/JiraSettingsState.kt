package com.theboringdevelopers.lenter.settings

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

@Service
@State(
    name = "JiraSettings",
    storages = [Storage("LenterJiraSettings.xml")]
)
class JiraSettingsState : PersistentStateComponent<JiraSettingsState> {

    private val logger = Logger.getInstance(JiraSettingsState::class.java)

    var jiraUrl: String = ""
    var jiraUsername: String = ""
    var jiraApiToken: String = ""
    var jiraProjectId: String = ""
    var jiraProjectKey: String = ""
    var jiraIssueType: String = ""
    var jiraPriority: String = ""

    override fun getState(): JiraSettingsState = this

    override fun loadState(state: JiraSettingsState) {
        jiraUrl = state.jiraUrl
        jiraUsername = state.jiraUsername
        jiraApiToken = state.jiraApiToken
        jiraProjectId = state.jiraProjectId
        jiraProjectKey = state.jiraProjectKey
        jiraIssueType = state.jiraIssueType
        jiraPriority = state.jiraPriority
    }

    fun readLocalProperties(): JiraLocalProperties? {
        val (origin, properties) = loadLocalProperties() ?: return null

        val defaults = JiraLocalProperties(
            origin = origin,
            jiraUrl = properties.findFirstNonBlank("jira.url")?.trimEnd('/'),
            jiraUsername = properties.findFirstNonBlank("jira.username", "jiraUser"),
            jiraApiToken = properties.findFirstNonBlank("jira.apiToken", "jira.token"),
            jiraProjectId = properties.findFirstNonBlank("jira.projectId"),
            jiraProjectKey = properties.findFirstNonBlank("jira.projectKey"),
            jiraIssueType = properties.findFirstNonBlank("jira.issueType", "jira.issueTypeId"),
            jiraPriority = properties.findFirstNonBlank("jira.priority", "jira.priorityId"),
        )

        if (defaults.hasAnyValue()) {
            logger.info("Loaded Jira settings from $origin")
            return defaults
        }

        logger.info("local.properties at $origin does not contain Jira settings")
        return null
    }

    private fun loadLocalProperties(): LoadedProperties? {
        val path = localPropertiesPath() ?: return null
        val properties = Properties()

        return runCatching {
            Files.newBufferedReader(path).use(properties::load)
            LoadedProperties(path, properties)
        }.onFailure {
            logger.warn("Failed to read local.properties from $path", it)
        }.getOrNull()
    }

    private fun localPropertiesPath(): Path? {
        val overridePath = sequenceOf(
            System.getProperty("lenter.local.properties"),
            System.getenv("LENTER_LOCAL_PROPERTIES"),
        )
            .firstNotNullOfOrNull { it?.takeIf(String::isNotBlank) }
            ?.let { Paths.get(it.trim()) }

        if (overridePath != null) {
            if (Files.isRegularFile(overridePath)) {
                return overridePath
            }
            logger.warn("Local properties override path '$overridePath' does not exist or is not a file")
        }

        val defaultPath = sequenceOfNotNull(
            System.getProperty("user.dir")?.let { Paths.get(it) },
            PathManager.getPluginsPath()?.let { Paths.get(it) },
            PathManager.getHomePath()?.let { Paths.get(it) },
        )
            .map { it.resolve("local.properties") }
            .firstOrNull(Files::isRegularFile)

        return defaultPath
    }

    private fun Properties.findFirstNonBlank(vararg keys: String): String? = keys
        .asSequence()
        .mapNotNull { getProperty(it)?.trim() }
        .firstOrNull { it.isNotEmpty() }

    private data class LoadedProperties(val origin: Path, val properties: Properties)

    data class JiraLocalProperties(
        val origin: Path,
        val jiraUrl: String?,
        val jiraUsername: String?,
        val jiraApiToken: String?,
        val jiraProjectId: String?,
        val jiraProjectKey: String?,
        val jiraIssueType: String?,
        val jiraPriority: String?,
    ) {
        fun hasAnyValue(): Boolean = sequenceOf(
            jiraUrl,
            jiraUsername,
            jiraApiToken,
            jiraProjectId,
            jiraProjectKey,
            jiraIssueType,
            jiraPriority,
        ).any { !it.isNullOrBlank() }
    }

    companion object {
        fun getInstance(): JiraSettingsState = service()
    }
}
