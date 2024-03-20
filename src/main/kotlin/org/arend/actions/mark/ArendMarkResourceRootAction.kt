package org.arend.actions.mark

import com.intellij.ide.projectView.actions.MarkSourceRootAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.jps.model.java.JavaResourceRootType

class ArendMarkResourceRootAction : MarkSourceRootAction(JavaResourceRootType.RESOURCE) {
    override fun update(e: AnActionEvent) {
        if (hasSpecialDirectories(e)) {
            e.presentation.isEnabledAndVisible = false
        } else {
            e.presentation.isEnabledAndVisible = true
            super.update(e)
        }
    }
}
