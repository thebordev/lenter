package com.theboringdevelopers.lenter.feature.preview.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.ui.JBSplitter
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Split редактор: слева XML код, справа preview
 */
class VectorDrawableSplitEditor(
    project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val textEditor: TextEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    private val previewPanel: VectorDrawablePreviewPanel = VectorDrawablePreviewPanel(file)
    private val splitter: JBSplitter = JBSplitter(false, 0.5f).apply {
        firstComponent = textEditor.component
        secondComponent = previewPanel.component
        setHonorComponentsMinimumSize(true)
    }

    private val fileListener = object : VirtualFileListener {
        override fun propertyChanged(event: VirtualFilePropertyEvent) {
            if (event.file == file && event.propertyName == VirtualFile.PROP_WRITABLE) {
                previewPanel.refresh()
            }
        }
    }

    init {

        file.fileSystem.addVirtualFileListener(fileListener)

        textEditor.editor.document.addDocumentListener(
            VectorDrawableDocumentListener(previewPanel),
            this
        )
    }

    override fun dispose() {
        file.fileSystem.removeVirtualFileListener(fileListener)
        Disposer.dispose(textEditor)
        previewPanel.dispose()
    }

    override fun getComponent(): JComponent = splitter

    override fun getPreferredFocusedComponent(): JComponent? = textEditor.preferredFocusedComponent

    override fun getName(): String = "Vector Drawable"

    override fun setState(state: FileEditorState) {
        if (state is TextEditorState) {
            textEditor.setState(state)
        }
    }

    override fun getState(level: com.intellij.openapi.fileEditor.FileEditorStateLevel): FileEditorState {
        return textEditor.getState(level)
    }

    override fun isModified(): Boolean = textEditor.isModified

    override fun isValid(): Boolean = textEditor.isValid && file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        textEditor.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        textEditor.removePropertyChangeListener(listener)
    }

    override fun getFile(): VirtualFile = file
}
