package org.arend.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.indexing.FileBasedIndex
import org.arend.naming.reference.GlobalReferable
import gnu.trove.THashSet
import org.arend.psi.ArendFile
import java.util.*

class ArendCustomSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val elementToSearch = parameters.elementToSearch
        val standardName = (elementToSearch as? GlobalReferable)?.refName
        val aliasName = (elementToSearch as? GlobalReferable)?.aliasName
        val scope = parameters.scopeDeterminedByUser

        val fileBasedIndex = FileBasedIndex.getInstance()
        val project = parameters.project

        if (scope is GlobalSearchScope && standardName != null)
            ApplicationManager.getApplication().runReadAction {
                fun doSearch(name: String) {
                    val fileSet = THashSet<VirtualFile>()
                    fileBasedIndex.getFilesWithKey(IdIndex.NAME, Collections.singleton(IdIndexEntry(name, true)), Processors.cancelableCollectProcessor(fileSet), scope)
                    fileSet.mapNotNull { PsiManager.getInstance(project).findFile(it) }.filterIsInstance<ArendFile>()
                            .forEach { parameters.optimizer.searchWord(name, LocalSearchScope(it), true, elementToSearch) }
                }

                doSearch(standardName)
                if (aliasName != null) doSearch(aliasName)
            } else if (aliasName != null && standardName != null) {
            parameters.optimizer.takeSearchRequests()
            parameters.optimizer.searchWord(aliasName, scope,true, elementToSearch)
            parameters.optimizer.searchWord(standardName, scope, true, elementToSearch)
        }
    }

}