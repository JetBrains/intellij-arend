package org.arend.injection.actions

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.editor.Document
import org.arend.injection.InjectedArendEditor

class RollbackConfigAction<T>(
    private val editor: InjectedArendEditor,
    private val document: Document,
    private val map: MutableMap<T, Int>,
    private val key: T,
    private val id: String?
) : UndoableAction {

    override fun undo() {
        map.computeIfPresent(key) { _, num -> num - 1 }
        if (map[key] == 0) {
            map.remove(key)
        }
        document.setReadOnly(true)
        editor.updateErrorText(id)
    }

    override fun redo() {
        document.setReadOnly(false)
        map[key] = map.getOrDefault(key, 0) + 1
    }

    override fun getAffectedDocuments(): Array<DocumentReference> {
        return arrayOf(DocumentReferenceManager.getInstance().create(document))
    }

    override fun isGlobal(): Boolean {
        return false
    }

}