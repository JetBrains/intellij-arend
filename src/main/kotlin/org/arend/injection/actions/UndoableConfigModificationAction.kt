package org.arend.injection.actions

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.editor.Document
import org.arend.injection.InjectedArendEditor

class UndoableConfigModificationAction<T>(
    private val editor: InjectedArendEditor,
    private val document: Document,
    private val map: MutableMap<T, Int>,
    private val key: T,
    private val inverted: Boolean,
    private val id: String?
) : UndoableAction {

    override fun undo() {
        if (inverted) increase() else decrease()
        editor.updateErrorText(id)
    }

    override fun redo() {
        if (inverted) decrease() else increase()
    }

    private fun increase() {
        map[key] = map.getOrDefault(key, 0) + 1
    }

    private fun decrease() {
        map.computeIfPresent(key) { _, num -> num - 1 }
        if (map[key] == 0) {
            map.remove(key)
        }
    }

    override fun getAffectedDocuments(): Array<DocumentReference> {
        return arrayOf(DocumentReferenceManager.getInstance().create(document))
    }

    override fun isGlobal(): Boolean {
        return false
    }

}