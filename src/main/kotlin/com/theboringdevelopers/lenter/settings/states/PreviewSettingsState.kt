package com.theboringdevelopers.lenter.settings.states

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.theboringdevelopers.lenter.settings.states.PreviewSettingsState",
    storages = [Storage("LenterPreviewSettings.xml")]
)
class PreviewSettingsState : PersistentStateComponent<PreviewSettingsState> {

    var composeColorPreviewEnabled: Boolean = true
    var drawablePreviewInCodeEnabled: Boolean = true
    var drawablePreviewInTreeEnabled: Boolean = true

    override fun getState(): PreviewSettingsState = this

    override fun loadState(state: PreviewSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): PreviewSettingsState {
            return ApplicationManager.getApplication()
                .getService(PreviewSettingsState::class.java)
        }
    }
}