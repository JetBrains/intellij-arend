package org.arend.actions

import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.ArendFileType
import java.util.Collections.singletonList

class SearchArendFilesContributor(event: AnActionEvent) : AbstractGotoSEContributor(event) {
    override fun getGroupName(): String = "Arend files"

    override fun getSortWeight(): Int = 201

    override fun createModel(project: Project): FilteringGotoByModel<*> {
        val model = object : GotoFileModel(project) {
            override fun acceptItem(item: NavigationItem?): Boolean {
                if (item !is PsiFile) return false
                return super.acceptItem(item)
            }
        }
        model.setFilterItems(singletonList(FileTypeRef.forFileType(ArendFileType)))
        return model
    }
}

class ArendSECFactory<T> : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> {
        return SearchArendFilesContributor(initEvent)
    }
}