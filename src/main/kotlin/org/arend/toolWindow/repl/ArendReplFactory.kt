package org.arend.toolWindow.repl

import com.intellij.execution.console.LanguageConsoleBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.content.ContentFactory
import org.arend.ArendLanguage
import org.arend.psi.ArendPsiFactory


class ArendReplFactory : ToolWindowFactory, DumbAware {
    companion object Constants {
        const val TITLE = "Arend REPL"
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val consoleView = LanguageConsoleBuilder()
            .psiFileFactory { v, p ->
                PsiManager.getInstance(p).findFile(v) ?: createFile(p, v)
            }
            .build(project, ArendLanguage.INSTANCE)
        Disposer.register(toolWindow.disposable, consoleView)
        val toolWindowPanel = SimpleToolWindowPanel(false, false)
        toolWindowPanel.setContent(consoleView.component)
        val group = DefaultActionGroup(
            object : DumbAwareAction("Clear", null, AllIcons.Actions.GC) {
                override fun actionPerformed(event: AnActionEvent) = WriteCommandAction.writeCommandAction(project).run<Exception> {
                    val document = consoleView.editorDocument
                    document.deleteString(0, document.textLength)
                }
            }
        )
        val toolbar = ActionManager.getInstance().createActionToolbar(TITLE, group, true)
        toolWindowPanel.toolbar = toolbar.component
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(toolWindowPanel.component, null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun createFile(p: Project, v: VirtualFile) = ArendPsiFactory(p)
        .createFromText(v.inputStream.reader().readText())
}
