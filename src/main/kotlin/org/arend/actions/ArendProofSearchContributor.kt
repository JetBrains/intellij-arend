package org.arend.actions

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import org.arend.psi.ArendDefFunction
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.util.arendModules
import javax.swing.ListCellRenderer

class ArendProofSearchContributor(val event: AnActionEvent) : WeightedSearchEverywhereContributor<PsiReferable> {
    override fun getGroupName(): String = "Proof search"

    override fun getSortWeight(): Int = 201

    override fun isShownInSeparateTab(): Boolean {
        return event.project?.arendModules?.isNotEmpty() ?: false
    }

    override fun getElementsRenderer(): ListCellRenderer<Any> {
        return object : SearchEverywherePsiRenderer(this) {

            override fun getElementText(element: PsiElement): String {
                val superText = super.getElementText(element)
                if (element is ArendDefFunction && element.returnExpr != null) {
                    return "$superText : ${element.returnExpr?.text}"
                }
                return superText
            }
        }
    }

    override fun getSearchProviderId(): String = ArendProofSearchContributor::class.java.simpleName

    override fun showInFindResults(): Boolean = false

    override fun isDumbAware(): Boolean {
        return false
    }

    override fun processSelectedItem(selected: PsiReferable, modifiers: Int, searchText: String): Boolean {
        // todo: maybe filling selected goal with the proof?
        return true
    }

    override fun getDataForItem(element: PsiReferable, dataId: String): Any? = null

    override fun fetchWeightedElements(
        pattern: String,
        progressIndicator: ProgressIndicator,
        consumer: Processor<in FoundItemDescriptor<PsiReferable>>
    ) {
        val project = event.project ?: return
        runReadAction {
            val keys = StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, event.project!!)
            for (definitionName in keys) {
                if (progressIndicator.isCanceled) {
                    break
                }
                StubIndex.getInstance().processElements(
                    ArendDefinitionIndex.KEY,
                    definitionName,
                    project,
                    GlobalSearchScope.allScope(project),
                    PsiReferable::class.java
                ) { def ->
                    if (progressIndicator.isCanceled) {
                        return@processElements false
                    }
                    if (def.name?.contains(pattern) == true) {
                        // todo: weight
                        consumer.process(FoundItemDescriptor(def, 1))
                    }
                    true
                }
            }
        }
    }
}

class ArendProofSearchFactory : SearchEverywhereContributorFactory<PsiReferable> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<PsiReferable> {
        return ArendProofSearchContributor(initEvent)
    }
}