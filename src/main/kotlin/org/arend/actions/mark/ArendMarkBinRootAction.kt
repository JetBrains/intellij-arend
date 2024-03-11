package org.arend.actions.mark

import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.arend.util.ArendBundle

class ArendMarkBinRootAction : MarkExcludeRootAction() {

    init {
        templatePresentation.text = ArendBundle.message("arend.icon.modules.binRoot.tooltip")
    }

    override fun update(e: AnActionEvent) {
        if (isNotOtherDirectionType(e)) {
            e.presentation.isEnabledAndVisible = true
            super.update(e)
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        unmarkOldDirectory(e, DirectoryType.BIN)
        super.actionPerformed(e)
    }
}