package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import org.arend.injection.InjectedArendEditor
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement

class ArendMessagesViewEditor(project: Project, treeElement: ArendErrorTreeElement, private val isGoalEditor: Boolean)
    : InjectedArendEditor(project, "Arend Messages", treeElement) {
    init {
        setupActions()
    }

    fun update(newTreeElement: ArendErrorTreeElement) {
        treeElement = newTreeElement
        updateErrorText()
        actionGroup.removeAll()
        setupActions()
    }

    fun clear() {
        clearText()
        actionGroup.removeAll()
        treeElement = null
    }

    private fun setupActions() {
        if (isGoalEditor) {
            actionGroup.add(ActionManager.getInstance().getAction(ArendPinGoalAction.ID))
            actionGroup.add(ActionManager.getInstance().getAction(ArendClearGoalAction.ID))
            actionGroup.addSeparator()
            actionGroup.add(createPrintOptionsActionGroup())
        } else {
            actionGroup.add(ActionManager.getInstance().getAction(ArendPinErrorAction.ID))
            actionGroup.addSeparator()
            actionGroup.add(createPrintOptionsActionGroup())
            actionGroup.add(ArendShowGoalsInErrorsPanelAction())
        }
    }

    private fun createPrintOptionsActionGroup(): ArendPrintOptionsActionGroup {
        val enablePrintOptions = treeElement?.errors?.any { it.error.hasExpressions() } ?: false
        return ArendPrintOptionsActionGroup(project, printOptionKind, enablePrintOptions)
    }
}