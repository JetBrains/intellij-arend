package org.arend.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.indexing.FileBasedIndex
import gnu.trove.THashSet
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import java.util.*
import kotlin.collections.ArrayList

class ArendCustomSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val elementToSearch = parameters.elementToSearch as? PsiLocatedReferable ?: return
        val scope = parameters.scopeDeterminedByUser
        val fileBasedIndex = FileBasedIndex.getInstance()
        val project = parameters.project
        val tasks = ArrayList<Pair<String, SearchScope>>()

        runReadAction {
            val standardName = elementToSearch.refName
            val aliasName = elementToSearch.aliasName

            if (scope is GlobalSearchScope) {
                fun collectFiles(name: String) {
                    val fileSet = THashSet<VirtualFile>()
                    fileBasedIndex.getFilesWithKey(IdIndex.NAME, Collections.singleton(IdIndexEntry(name, true)), Processors.cancelableCollectProcessor(fileSet), scope)
                    fileSet.mapNotNull { PsiManager.getInstance(project).findFile(it) }.filterIsInstance<ArendFile>()
                        .forEach { tasks.add(Pair(name, LocalSearchScope(it))) }
                }

                collectFiles(standardName)
                if (aliasName != null) collectFiles(aliasName)
            } else if (aliasName != null) {
                tasks.add(Pair(aliasName, scope))
            }
        }

        for (task in tasks) {
            parameters.optimizer.searchWord(task.first, task.second, true, elementToSearch)
        }
    }

}