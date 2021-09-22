package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import org.arend.injection.InjectedArendEditor
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement

class ArendMessagesViewEditor(project: Project, treeElement: ArendErrorTreeElement)
    : InjectedArendEditor(project, "Arend Messages", treeElement) {
    init {
        setupActions()
    }

    private fun setupActions() {
        actionGroup.add(ActionManager.getInstance().getAction("Arend.PinErrorMessage"))
        val enablePrintOptions = treeElement?.errors?.any { it.error.hasExpressions() } ?: false
        actionGroup.add(ArendPrintOptionsActionGroup(project, printOptionKind, enablePrintOptions))
    }
}