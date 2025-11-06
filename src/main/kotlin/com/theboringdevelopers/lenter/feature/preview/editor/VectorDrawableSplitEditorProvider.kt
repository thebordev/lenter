package com.theboringdevelopers.lenter.feature.preview.editor

import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.theboringdevelopers.lenter.settings.states.PreviewSettingsState

/**
 * Provider для создания split-редактора с кодом и preview
 */
class VectorDrawableSplitEditorProvider : AsyncFileEditorProvider, DumbAware {

    companion object {
        private const val EDITOR_TYPE_ID = "vector-drawable-split-editor"
    }

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!PreviewSettingsState.getInstance().vectorDrawableEditorPreviewEnabled) {
            return false
        }

        if (file.extension?.lowercase() != "xml") {
            return false
        }

        val path = file.path
        return path.contains("/drawable") && (
                path.contains("/res/drawable") ||
                        path.contains("/composeResources/drawable")
                )
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return VectorDrawableSplitEditor(project, file)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
        return object : AsyncFileEditorProvider.Builder() {
            override fun build(): FileEditor {
                return VectorDrawableSplitEditor(project, file)
            }
        }
    }
}
