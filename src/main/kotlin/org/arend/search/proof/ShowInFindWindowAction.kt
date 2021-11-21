package org.arend.search.proof

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
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

class ShowInFindWindowAction(private val ui: ProofSearchUI, private val project: Project) : DumbAwareAction(
    IdeBundle.messagePointer("show.in.find.window.button.name"),
    IdeBundle.messagePointer("show.in.find.window.button.description"), AllIcons.General.Pin_tab
) {
    override fun actionPerformed(e: AnActionEvent) {
        ui.close()
        val searchText: String = ui.searchField.text
        val presentation = UsageViewPresentation()
        presentation.codeUsagesString = "Matches of $searchText"
        presentation.targetsNodeText = "Results"
        presentation.tabName = "Matches"
        presentation.tabText = "Matches"
        val usages: MutableCollection<Usage> = LinkedHashSet()
        val targets: MutableCollection<PsiElement?> = LinkedHashSet()
        ProgressManager.getInstance().run(object : Task.Modal(project, "Matches", true) {

            private val progressIndicator: ProgressIndicator = ProgressIndicatorBase()

            override fun run(indicator: ProgressIndicator) {
                progressIndicator.start()
                val foundElements: MutableCollection<Any> = ArrayList()
                val elements = fetchWeightedElements(project, searchText, progressIndicator)
                foundElements.add(elements.toList())
                fillUsages(foundElements, usages, targets)
            }

            override fun onCancel() {
                progressIndicator.cancel()
            }

            override fun onSuccess() {
                showInFindWindow(targets, usages, presentation)
            }

            override fun onThrowable(error: Throwable) {
                super.onThrowable(error)
                progressIndicator.cancel()
            }
        })
    }

    private fun fillUsages(
        foundElements: Collection<Any>,
        usages: MutableCollection<in Usage>,
        targets: MutableCollection<in PsiElement?>
    ) {
        runReadAction {
            foundElements.map { o: Any? ->
                (o as FoundItemDescriptor<ProofSearchEntry>).item.def
            }
                .forEach { element: PsiElement? ->
                    if (element!!.textRange != null) {
                        val usageInfo = UsageInfo(element)
                        usages.add(UsageInfo2UsageAdapter(usageInfo))
                    } else {
                        targets.add(element)
                    }
                }
        }
    }

    private fun showInFindWindow(
        targets: Collection<PsiElement?>,
        usages: Collection<Usage>,
        presentation: UsageViewPresentation
    ) {
        val targetsArray: Array<out UsageTarget> = PsiElement2UsageTargetAdapter.convert(targets.toTypedArray(), false)
        val usagesArray = usages.toTypedArray<Usage>()
        UsageViewManager.getInstance(project).showUsages(targetsArray, usagesArray, presentation)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        e.presentation.icon =
            ToolWindowManager.getInstance(project).getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab)
    }
}
