package org.arend.actions.mark

import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent

class ArendMarkExcludeRootAction : MarkExcludeRootAction() {
    init {
        templatePresentation.text = ActionsBundle.message("action.MarkExcludeRoot.text")
    }

    override fun update(e: AnActionEvent) {
        if (hasSpecialDirectories(e)) {
            e.presentation.isEnabledAndVisible = false
        } else {
            e.presentation.isEnabledAndVisible = true
            super.update(e)
        }
    }
}