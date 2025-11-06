package com.theboringdevelopers.lenter.feature.preview.editor

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.util.Alarm

/**
 * Слушает изменения в документе и обновляет preview с задержкой
 */
class VectorDrawableDocumentListener(
    private val previewPanel: VectorDrawablePreviewPanel
) : DocumentListener {

    private val alarm = Alarm(
        Alarm.ThreadToUse.SWING_THREAD,
        previewPanel,
    )

    override fun documentChanged(event: DocumentEvent) {
        alarm.cancelAllRequests()

        alarm.addRequest({ previewPanel.refresh() }, 500)
    }
}
