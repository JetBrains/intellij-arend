package org.arend.actions

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.ide.util.gotoByName.LanguageRef
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.ArendLanguage
import org.arend.psi.ArendDefFunction
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.arendModules
import java.util.Collections.singletonList
import javax.swing.ListCellRenderer

class ArendProofSearchContributor(val event: AnActionEvent) : AbstractGotoSEContributor(event) {
    override fun getGroupName(): String = "Proof search"

    override fun getSortWeight(): Int = 201

    override fun isShownInSeparateTab(): Boolean {
        return event.project?.arendModules?.isNotEmpty() ?: false
    }

    override fun createModel(project: Project): FilteringGotoByModel<*> {
        val model = object : GotoSymbolModel2(project) {

            override fun acceptItem(item: NavigationItem?): Boolean {
                if (item !is ArendCompositeElement) return false
                return super.acceptItem(item)
            }
        }
        model.setFilterItems(singletonList(LanguageRef.forLanguage(ArendLanguage.INSTANCE)))
        return model
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

    override fun getActions(onChanged: Runnable): MutableList<AnAction> {
        return singletonList(
            doGetActions(
                FileSearchEverywhereContributor.createFileTypeFilter(this.myProject),
                null,
                onChanged
            ).first()
        )
    }
}

class ArendProofSearchFactory : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> {
        return ArendProofSearchContributor(initEvent)
    }
}