package org.arend.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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
import com.intellij.util.containers.mapSmartSet
import com.intellij.util.indexing.FileBasedIndex
import gnu.trove.THashSet
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendAliasIdentifier
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.typechecking.TypeCheckingService

class ArendCustomSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        var elementToSearch_var : PsiLocatedReferable? = null
        runReadAction {
            elementToSearch_var = when (val e = parameters.elementToSearch) {
                is PsiLocatedReferable -> e
                is ArendAliasIdentifier -> e.parent?.parent as? PsiLocatedReferable
                else -> null
            }
        }
        val elementToSearch = elementToSearch_var ?: return
        val scope = parameters.scopeDeterminedByUser
        val project = parameters.project
        val tasks = ArrayList<Pair<String, SearchScope>>()


        runReadAction {
            val standardName = elementToSearch.refName
            val aliasName = elementToSearch.aliasName
            if (scope is GlobalSearchScope) {
                collectSearchScopes(listOf(standardName), scope, project).forEach {
                    val arendFile = PsiManager.getInstance(project).findFile(it) as? ArendFile
                    if (arendFile != null) {
                        tasks.add(standardName to LocalSearchScope(arendFile))
                    }
                }
                if (aliasName != null) {
                    collectSearchScopes(listOf(aliasName), scope, project).forEach {
                        val arendFile = PsiManager.getInstance(project).findFile(it) as? ArendFile
                        if (arendFile != null) {
                            tasks.add(aliasName to LocalSearchScope(arendFile))
                        }
                    }
            }
            } else if (aliasName != null) {
                tasks.add(Pair(aliasName, scope))
            }
        }

        for (task in tasks) {
            parameters.optimizer.searchWord(task.first, task.second, true, elementToSearch)
        }
    }

}

/**
 * Every returned file contains **all** of identifiers speicified in [namesToSearch]
 */
fun collectSearchScopes(namesToSearch: List<String>, scope: GlobalSearchScope, project: Project): List<VirtualFile> =
    runReadAction {
        val fileBasedIndex = FileBasedIndex.getInstance()
        val fileSet = THashSet<VirtualFile>()
        fileBasedIndex.getFilesWithKey(
            IdIndex.NAME,
            namesToSearch.mapSmartSet { IdIndexEntry(it, true) },
            Processors.cancelableCollectProcessor(fileSet),
            scope
        )
        val localScopes = fileSet
            .filterTo(ArrayList()) { PsiManager.getInstance(project).findFile(it) is ArendFile }
        if (scope.isSearchInLibraries) {
            project.service<TypeCheckingService>().prelude?.let {
                localScopes.add(it.virtualFile)
            }
        }
        localScopes
    }