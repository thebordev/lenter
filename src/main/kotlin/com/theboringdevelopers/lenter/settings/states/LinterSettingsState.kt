package com.theboringdevelopers.lenter.settings.states

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.theboringdevelopers.lenter.settings.states.LenterSettings",
    storages = [Storage("LenterLinterSettings.xml")]
)
class LinterSettingsState : PersistentStateComponent<LinterSettingsState> {

    var ollamaApiUrl: String = "http://localhost:11434"
    var modelName: String = "qwen2.5-coder:7b"
    var requestTimeoutSeconds: Int = 60
    var ollamaModelsPath: String = ""

    override fun getState(): LinterSettingsState = this

    override fun loadState(state: LinterSettingsState) {
        ollamaApiUrl = state.ollamaApiUrl
        modelName = state.modelName
        requestTimeoutSeconds = state.requestTimeoutSeconds
        ollamaModelsPath = state.ollamaModelsPath
    }

    companion object {
        fun getInstance(): LinterSettingsState {
            return ApplicationManager.getApplication().getService(LinterSettingsState::class.java)
        }
    }
}