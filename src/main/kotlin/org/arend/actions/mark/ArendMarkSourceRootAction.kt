package org.arend.actions.mark

import com.intellij.ide.projectView.actions.MarkSourceRootAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.jps.model.java.JavaSourceRootType

class ArendMarkSourceRootAction : MarkSourceRootAction(JavaSourceRootType.SOURCE) {
    override fun update(e: AnActionEvent) {
        if (isNotOtherDirectionType(e)) {
            e.presentation.isEnabledAndVisible = true
            super.update(e)
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        unmarkOldDirectory(e, DirectoryType.SRC)
        super.actionPerformed(e)
    }
}
