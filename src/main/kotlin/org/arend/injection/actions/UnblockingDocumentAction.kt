package org.arend.injection.actions

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.command.undo.UnexpectedUndoException
import com.intellij.openapi.editor.Document

/**
 * A hack to make [document] writeable before running 'Undo'
 */
class UnblockingDocumentAction(private val document: Document, private val id: String?, private val unblockWhenUndo: Boolean) : UndoableAction {

    override fun undo() {
        if (id != null) {
            document.setReadOnly(!unblockWhenUndo)
        } else {
            throw UnexpectedUndoException("Undo is not possible")
        }
    }

    override fun redo() {
        if (id != null) {
            document.setReadOnly(unblockWhenUndo)
        } else {
            throw UnexpectedUndoException("Undo is not possible")
        }
    }

    override fun getAffectedDocuments(): Array<DocumentReference> {
        return arrayOf(DocumentReferenceManager.getInstance().create(document))
    }

    override fun isGlobal(): Boolean {
        return false
    }

}
