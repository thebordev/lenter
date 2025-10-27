package com.theboringdevelopers.commentator.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "CommentatorSettings",
    storages = [Storage("CommentatorSettings.xml")]
)
class CommentatorSettingsState : PersistentStateComponent<CommentatorSettingsState> {

    var ollamaApiUrl: String = "http://localhost:11434"
    var modelName: String = "qwen2.5-coder:7b"
    var requestTimeoutSeconds: Int = 60
    var ollamaModelsPath: String = ""

    override fun getState(): CommentatorSettingsState = this

    override fun loadState(state: CommentatorSettingsState) {
        ollamaApiUrl = state.ollamaApiUrl
        modelName = state.modelName
        requestTimeoutSeconds = state.requestTimeoutSeconds
        ollamaModelsPath = state.ollamaModelsPath
    }

    companion object {
        fun getInstance(): CommentatorSettingsState {
            return ApplicationManager.getApplication().getService(CommentatorSettingsState::class.java)
        }
    }
}