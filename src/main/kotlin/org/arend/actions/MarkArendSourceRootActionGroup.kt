package org.arend.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.containers.toArray
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

class MarkArendSourceRootActionGroup : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val actions = mutableListOf<AnAction>()
        for (type in listOf(
            JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE,
            JavaResourceRootType.RESOURCE, JavaResourceRootType.TEST_RESOURCE
        )) {
            actions.add(ArendMarkSourceRootAction(type!!))
        }
        return actions.toArray(emptyArray())
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
