package org.arend.actions

import com.intellij.openapi.actionSystem.*
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
        DefaultActionGroup(null, true).apply {
          templatePresentation.icon = ArendIcons.SHOW_IMPLICITS
          add(action(project, ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS).apply {
            templatePresentation.apply {
              text = "Show implicits"
              description = "Show implicit arguments in pop-ups"
            }
          })
          add(action(project, ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM).apply {
            templatePresentation.apply {
              text = "Show types in lambdas"
              description = "Show types in lambdas in pop-ups"
            }
          })
        }
    )
  }

  private fun action(project: Project, flag: ToAbstractVisitor.Flag) =
      ArendPrintOptionsFilterAction(project, PrintOptionKind.POPUP_PRINT_OPTIONS, flag)
}