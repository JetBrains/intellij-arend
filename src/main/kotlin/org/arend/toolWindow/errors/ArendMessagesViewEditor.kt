package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.error.GeneralError
import org.arend.injection.InjectedArendEditor
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement

class ArendMessagesViewEditor(project: Project, treeElement: ArendErrorTreeElement, private val isGoalEditor: Boolean)
    : InjectedArendEditor(project, "Arend Messages", treeElement) {

    override val printOptionKind: PrintOptionKind
        get() = when (treeElement?.highestError?.error?.level) {
            GeneralError.Level.GOAL -> PrintOptionKind.GOAL_PRINT_OPTIONS
            else -> PrintOptionKind.ERROR_PRINT_OPTIONS
        }

    init {
        setupActions()
    }

    fun update(newTreeElement: ArendErrorTreeElement) {
        val current = treeElement
        if (current?.errors == newTreeElement.errors) {
            newTreeElement.enrichNormalizationCache(current)
        }
        treeElement = newTreeElement
        updateErrorText()
    }

    fun updateActionGroup() {
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
            actionGroup.add(ArendShowImplicitGoalsAction())
        } else {
            actionGroup.add(ActionManager.getInstance().getAction(ArendPinErrorAction.ID))
            actionGroup.addSeparator()
            actionGroup.add(createPrintOptionsActionGroup())
            actionGroup.add(ArendShowGoalsInErrorsPanelAction())
        }
    }

    private fun createPrintOptionsActionGroup(): ArendPrintOptionsActionGroup {
        val enablePrintOptions = treeElement?.errors?.any { it.error.hasExpressions() } ?: false
        return ArendPrintOptionsActionGroup(project, printOptionKind, {
            when (printOptionKind) {
                PrintOptionKind.GOAL_PRINT_OPTIONS -> project.service<ArendMessagesService>().updateGoalText()
                else -> project.service<ArendMessagesService>().updateErrorText()
            }
        }, enablePrintOptions)
    }
}