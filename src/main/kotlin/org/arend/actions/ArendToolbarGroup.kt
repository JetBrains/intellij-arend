package org.arend.actions

import com.intellij.openapi.actionSystem.*
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.PrintOptionKind

class ArendToolbarGroup : ActionGroup() {
  override fun getChildren(eNull: AnActionEvent?): Array<AnAction> {
    val e = eNull ?: return emptyArray()
    val project = e.project ?: return emptyArray()
    if (project.isDisposed) return emptyArray()
    return arrayOf(
        Separator.getInstance(),
        ArendNormalizeToggleAction,
        ArendPrintOptionsActionGroup(project, PrintOptionKind.POPUP_PRINT_OPTIONS)
    )
  }
}