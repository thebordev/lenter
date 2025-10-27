package com.theboringdevelopers.lenter.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(
    name = "JiraSettings",
    storages = [Storage("LenterJiraSettings.xml")]
)
class JiraSettingsState : PersistentStateComponent<JiraSettingsState> {

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

    companion object {
        fun getInstance(): JiraSettingsState = service()
    }
}