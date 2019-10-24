package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.settings.ArendProjectSettings

class ArendPrintOptionsFilterAction(private val project: Project, private val flag: ToAbstractVisitor.Flag, private val group: ArendPrintOptionsActionGroup)
    : ToggleAction(flagToString(flag), null, null), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    private val isSelected: Boolean
        get() {
            val printOptionSet = project.service<ArendProjectSettings>().printOptionsFilterSet
            return printOptionSet.contains(flag)
        }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val printOptionSet = project.service<ArendProjectSettings>().printOptionsFilterSet
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
        fun flagToString(flag: ToAbstractVisitor.Flag): String = when (flag) {
            ToAbstractVisitor.Flag.HIDE_HIDEABLE_DEFINITIONS -> "Hide hideable? definitions"
            ToAbstractVisitor.Flag.SHOW_CON_PARAMS -> "Show constructor parameters"
            ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE -> "Show field instances"
            ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS -> "Show implicit arguments"
            ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM -> "Show types in lambda expressions"
            ToAbstractVisitor.Flag.SHOW_PREFIX_PATH -> "Show prefix path"
            ToAbstractVisitor.Flag.SHOW_BIN_OP_IMPLICIT_ARGS -> "Show binary operations' implicit arguments"
            ToAbstractVisitor.Flag.SHOW_CASE_RESULT_TYPE -> "Show result types of case expressions"
            ToAbstractVisitor.Flag.SHOW_INFERENCE_LEVEL_VARS -> "Show level inference variables"
        }
    }
}