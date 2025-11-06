package com.theboringdevelopers.lenter.settings.states

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Настройки предпросмотра ресурсов в IDE
 */
@State(
    name = "com.theboringdevelopers.lenter.settings.states.PreviewSettingsState",
    storages = [Storage("LenterPreviewSettings.xml")]
)
class PreviewSettingsState : PersistentStateComponent<PreviewSettingsState> {

    // Compose Color Preview
    var composeColorPreviewEnabled: Boolean = true

    // Drawable Preview
    var drawablePreviewInCodeEnabled: Boolean = true
    var drawablePreviewInTreeEnabled: Boolean = true

    // Vector Drawable Editor Preview
    var vectorDrawableEditorPreviewEnabled: Boolean = true
    var vectorDrawablePreviewSize: Int = 256

    // String Resource Preview
    var stringResourcePreviewEnabled: Boolean = true
    var stringResourcePreferredLanguage: String = "default"

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
