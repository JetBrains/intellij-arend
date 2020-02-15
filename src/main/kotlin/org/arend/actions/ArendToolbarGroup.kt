package org.arend.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind

class ArendToolbarGroup : ActionGroup() {
  override fun getChildren(eNull: AnActionEvent?): Array<AnAction> {
    val e = eNull ?: return emptyArray()
    val project = e.project ?: return emptyArray()
    if (project.isDisposed) return emptyArray()
    return arrayOf(
        Separator.getInstance(),
        action(project, ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS).apply {
          templatePresentation.apply {
            icon = ArendIcons.SHOW_IMPLICITS
            text = "Show Implicits"
            description = "Show implicit arguments in pop-ups"
          }
        }
    )
  }

  private fun action(project: Project, flag: ToAbstractVisitor.Flag) =
      ArendPrintOptionsFilterAction(project, PrintOptionKind.POPUP_PRINT_OPTIONS, flag)
}