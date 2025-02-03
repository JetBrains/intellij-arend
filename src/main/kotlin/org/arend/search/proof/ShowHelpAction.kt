package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.arend.util.ArendBundle

class ShowHelpAction(private val ui: ProofSearchUI) : DumbAwareAction(
    ArendBundle.getLazyMessage("arend.proof.search.show.help"),
    ArendBundle.getLazyMessage("arend.proof.search.show.help.description"), AllIcons.General.ContextHelp
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        ui.close()
        BrowserUtil.browse("https://arend-lang.github.io/documentation/plugin-manual/navigating#proof-search")
    }

    override fun update(e: AnActionEvent) {
    }
}
