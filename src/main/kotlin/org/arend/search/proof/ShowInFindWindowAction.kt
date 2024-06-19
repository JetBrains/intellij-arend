package org.arend.search.proof

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.usages.*
import org.arend.util.ArendBundle

class ShowInFindWindowAction(private val ui: ProofSearchUI, private val project: Project) : DumbAwareAction(
    IdeBundle.messagePointer("show.in.find.window.button.name"),
    IdeBundle.messagePointer("show.in.find.window.button.description"), AllIcons.General.Pin_tab
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        ui.close()
        val searchText: String = ui.editorSearchField.text
        val presentation = UsageViewPresentation()
        presentation.codeUsagesString = ArendBundle.message("arend.proof.search.matches.of", searchText)
        presentation.targetsNodeText = ArendBundle.message("arend.proof.search.find.usages.results")
        presentation.tabName = ArendBundle.message("arend.proof.search.matches")
        presentation.tabText = ArendBundle.message("arend.proof.search.matches")
        val usages: MutableCollection<Usage> = LinkedHashSet()
        val targets: MutableCollection<PsiElement> = LinkedHashSet()
        ProgressManager.getInstance().run(object : Task.Modal(project, ArendBundle.message("arend.proof.search.searching.for.definitions"), true) {

            private val progressIndicator: ProgressIndicator = ProgressIndicatorBase()

            override fun run(indicator: ProgressIndicator) {
                progressIndicator.start()
                val elements = generateProofSearchResults(project, searchText).mapNotNull { it }.toList()
                fillUsages(elements, usages, targets)
            }

            override fun onCancel() = progressIndicator.cancel()

            override fun onSuccess() = showInFindWindow(targets, usages, presentation)

            override fun onThrowable(error: Throwable) {
                super.onThrowable(error)
                progressIndicator.cancel()
            }
        })
    }

    private fun fillUsages(
        foundElements: Collection<ProofSearchEntry>,
        usages: MutableCollection<in Usage>,
        targets: MutableCollection<in PsiElement>
    ) = runReadAction {
        foundElements
            .map(ProofSearchEntry::def)
            .forEach { element: PsiElement ->
                if (element.textRange != null) {
                    val usageInfo = UsageInfo(element)
                    usages.add(UsageInfo2UsageAdapter(usageInfo))
                } else {
                    targets.add(element)
                }
            }
    }

    private fun showInFindWindow(
        targets: Collection<PsiElement?>,
        usages: Collection<Usage>,
        presentation: UsageViewPresentation
    ) {
        val targetsArray: Array<out UsageTarget> = PsiElement2UsageTargetAdapter.convert(targets.toTypedArray(), false)
        UsageViewManager.getInstance(project).showUsages(targetsArray, usages.toTypedArray(), presentation)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        e.presentation.icon = ToolWindowManager.getInstance(project).getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab)
    }
}
