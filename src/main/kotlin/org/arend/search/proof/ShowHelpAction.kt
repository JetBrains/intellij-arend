package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.arend.util.ArendBundle

class ShowHelpAction(private val ui: SignatureSearchUI) : DumbAwareAction(
    ArendBundle.getLazyMessage("arend.signature.search.show.help"),
    ArendBundle.getLazyMessage("arend.signature.search.show.help.description"), AllIcons.General.ContextHelp
) {
    override fun actionPerformed(e: AnActionEvent) {
        ui.close()
        BrowserUtil.browse("https://arend-lang.github.io/documentation/signature-search")
    }

    override fun update(e: AnActionEvent) {
    }
}
