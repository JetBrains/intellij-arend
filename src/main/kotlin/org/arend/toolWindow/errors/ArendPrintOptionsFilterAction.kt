package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.settings.ArendProjectSettings

class ArendPrintOptionsFilterAction(private val project: Project,
                                    private val printOptionKind: PrintOptionKind,
                                    private val flag: ToAbstractVisitor.Flag)
    : ToggleAction(flagToString(flag), null, null), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    private val isSelected: Boolean
        get() {
            val printOptionSet = getFilterSet(project, printOptionKind)
            return printOptionSet.contains(flag)
        }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val printOptionSet = getFilterSet(project, printOptionKind)
        if (printOptionSet.contains(flag) == state) {
            return
        }

        if (state)
            printOptionSet.add(flag)
        else
            printOptionSet.remove(flag)

        project.service<ArendMessagesService>().update()
    }



    companion object {
        fun getFilterSet(project: Project, printOptionsKind: PrintOptionKind) = project.service<ArendProjectSettings>().let {
            when (printOptionsKind) {
                PrintOptionKind.ERROR_PRINT_OPTIONS -> it.errorPrintingOptionsFilterSet
                PrintOptionKind.GOAL_PRINT_OPTIONS -> it.goalPrintingOptionsFilterSet
            }
        }

        fun flagToString(flag: ToAbstractVisitor.Flag): String = when (flag) {
            ToAbstractVisitor.Flag.SHOW_COERCE_DEFINITIONS -> "Show coerce definitions"
            ToAbstractVisitor.Flag.SHOW_CON_PARAMS -> "Show constructor parameters"
            ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE -> "Show field instances"
            ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS -> "Show implicit arguments"
            ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM -> "Show types in lambda expressions"
            ToAbstractVisitor.Flag.SHOW_PREFIX_PATH -> "Show prefix path"
            ToAbstractVisitor.Flag.SHOW_BIN_OP_IMPLICIT_ARGS -> "Show infix operators' implicit arguments"
            ToAbstractVisitor.Flag.SHOW_CASE_RESULT_TYPE -> "Show result types of case expressions"
            ToAbstractVisitor.Flag.SHOW_INFERENCE_LEVEL_VARS -> "Show level inference variables"
        }
    }
}

enum class PrintOptionKind(val kindName: String) {
    GOAL_PRINT_OPTIONS("Goal"),
    ERROR_PRINT_OPTIONS("Error")
}