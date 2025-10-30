package com.theboringdevelopers.lenter.settings.states

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
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
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

@Service
@State(
    name = "com.theboringdevelopers.lenter.settings.states.JiraSettingsState",
    storages = [Storage("LenterJiraSettings.xml")]
)
class JiraSettingsState : PersistentStateComponent<JiraSettingsState.State> {

    companion object {
        private val LOG = Logger.getInstance(JiraSettingsState::class.java)
        private const val JIRA_TOKEN_KEY = "com.theboringdevelopers.lenter.jira.token"

        fun getInstance(): JiraSettingsState = service()
    }

    data class State(
        var localPropertiesPath: String = "",
        var jiraUrl: String = "",
        var jiraUsername: String = "",
        var jiraProjectId: String = "",
        var jiraProjectKey: String = "",
        var jiraIssueType: String = "",
        var jiraPriority: String = ""
    )

    private var myState = State()

    var localPropertiesPath: String
        get() = myState.localPropertiesPath
        set(value) { myState.localPropertiesPath = value }

    var jiraUrl: String
        get() = myState.jiraUrl
        set(value) { myState.jiraUrl = value }

    var jiraUsername: String
        get() = myState.jiraUsername
        set(value) { myState.jiraUsername = value }

    var jiraApiToken: String
        get() = getTokenFromPasswordSafe() ?: ""
        set(value) = saveTokenToPasswordSafe(value)

    var jiraProjectId: String
        get() = myState.jiraProjectId
        set(value) { myState.jiraProjectId = value }

    var jiraProjectKey: String
        get() = myState.jiraProjectKey
        set(value) { myState.jiraProjectKey = value }

    var jiraIssueType: String
        get() = myState.jiraIssueType
        set(value) { myState.jiraIssueType = value }

    var jiraPriority: String
        get() = myState.jiraPriority
        set(value) { myState.jiraPriority = value }

    override fun getState(): State = myState.copy()

    override fun loadState(state: State) {
        myState = state.copy()
    }

    fun readLocalProperties(filePath: String): JiraLocalProperties? {
        if (filePath.isBlank()) {
            LOG.info("local.properties path is empty")
            return null
        }

        val path = Paths.get(filePath)
        if (!path.exists() || !path.isRegularFile()) {
            LOG.warn("local.properties not found at: $filePath")
            return null
        }

        return loadPropertiesFromFile(path)
    }

    private fun loadPropertiesFromFile(path: Path): JiraLocalProperties? {
        return runCatching {
            val properties = Properties()
            Files.newBufferedReader(path).use { reader ->
                properties.load(reader)
            }

            val defaults = JiraLocalProperties(
                origin = path,
                jiraUrl = properties.findFirstNonBlank("jira.url")?.trimEnd('/'),
                jiraUsername = properties.findFirstNonBlank("jira.username", "jiraUser"),
                jiraApiToken = properties.findFirstNonBlank("jira.apiToken", "jira.token", "jira.pat"),
                jiraProjectId = properties.findFirstNonBlank("jira.projectId"),
                jiraProjectKey = properties.findFirstNonBlank("jira.projectKey"),
                jiraIssueType = properties.findFirstNonBlank("jira.issueType", "jira.issueTypeId"),
                jiraPriority = properties.findFirstNonBlank("jira.priority", "jira.priorityId")
            )

            if (defaults.hasAnyValue()) {
                LOG.info("Loaded Jira settings from $path")
                defaults
            } else {
                LOG.info("File $path does not contain Jira settings")
                null
            }
        }.onFailure { error ->
            LOG.warn("Failed to read properties from $path", error)
        }.getOrNull()
    }

    private fun getTokenFromPasswordSafe(): String? {
        val credentialAttributes = createCredentialAttributes()
        return PasswordSafe.Companion.instance.getPassword(credentialAttributes)
    }

    private fun saveTokenToPasswordSafe(token: String) {
        val credentialAttributes = createCredentialAttributes()
        val credentials = if (token.isNotBlank()) {
            Credentials(jiraUsername.takeIf { it.isNotBlank() } ?: "jira-user", token)
        } else {
            null
        }
        PasswordSafe.Companion.instance.set(credentialAttributes, credentials)
    }

    private fun createCredentialAttributes(): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("Lenter", JIRA_TOKEN_KEY),
            jiraUsername.takeIf { it.isNotBlank() } ?: "jira-user"
        )
    }

    private fun Properties.findFirstNonBlank(vararg keys: String): String? = keys
        .asSequence()
        .mapNotNull { getProperty(it)?.trim() }
        .firstOrNull { it.isNotEmpty() }

    data class JiraLocalProperties(
        val origin: Path,
        val jiraUrl: String?,
        val jiraUsername: String?,
        val jiraApiToken: String?,
        val jiraProjectId: String?,
        val jiraProjectKey: String?,
        val jiraIssueType: String?,
        val jiraPriority: String?
    ) {
        fun hasAnyValue(): Boolean = listOfNotNull(
            jiraUrl,
            jiraUsername,
            jiraApiToken,
            jiraProjectId,
            jiraProjectKey,
            jiraIssueType,
            jiraPriority
        ).any { it.isNotBlank() }
    }
}