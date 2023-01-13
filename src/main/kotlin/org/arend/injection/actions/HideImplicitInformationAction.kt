package org.arend.injection.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import org.arend.injection.InjectedArendEditor
import org.arend.util.isDetailedViewEditor

class HideImplicitInformationAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(PlatformDataKeys.EDITOR)?.isDetailedViewEditor() ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(PlatformDataKeys.EDITOR) ?: return
        val injectedEditor = editor.getUserData(InjectedArendEditor.AREND_GOAL_EDITOR) ?: return
        injectedEditor.performPrettyPrinterManipulation(editor, Choice.HIDE)
    }
}