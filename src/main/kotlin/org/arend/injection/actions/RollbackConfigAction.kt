package org.arend.injection.actions

import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.UndoableAction
import com.intellij.openapi.editor.Document
import org.arend.core.expr.Expression
import org.arend.injection.InjectedArendEditor

class RollbackConfigAction(
    private val editor: InjectedArendEditor,
    private val document: Document,
    private val map: MutableMap<Expression, Int>,
    private val core: Expression,
    private val id: String?
) : UndoableAction {

    override fun undo() {
        map.computeIfPresent(core) { _, num -> num - 1 }
        if (map[core] == 0) {
            map.remove(core)
        }
        document.setReadOnly(true)
        editor.updateErrorText(id)
    }

    override fun redo() {
        document.setReadOnly(false)
        map[core] = map.getOrDefault(core, 0) + 1
    }

    override fun getAffectedDocuments(): Array<DocumentReference> {
        return arrayOf(DocumentReferenceManager.getInstance().create(document))
    }

    override fun isGlobal(): Boolean {
        return false
    }

}