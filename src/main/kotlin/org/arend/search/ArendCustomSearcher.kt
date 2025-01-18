package org.arend.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.search.PsiSearchHelperImpl
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.containers.mapSmartSet
import com.intellij.util.indexing.FileBasedIndex
import org.arend.psi.ArendFile
import org.arend.psi.ArendFileScope
import org.arend.psi.ext.*
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler.Util.isDefIdentifierFromNsId
import org.arend.server.ArendServerService

class ArendCustomSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        var elementToSearch_var : PsiLocatedReferable? = null
        runReadAction {
            elementToSearch_var = when (val e = parameters.elementToSearch) {
                is PsiLocatedReferable -> e
                is ArendAliasIdentifier -> e.parent?.parent as? PsiLocatedReferable
                is ArendDefIdentifier -> if (isDefIdentifierFromNsId(e)) ((e.parent as ArendNsId).refIdentifier.resolve as? PsiLocatedReferable) else null
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
                collectSearchScopes(listOf(standardName), scope.isSearchInLibraries, project).forEach {
                    val arendFile = PsiManager.getInstance(project).findFile(it) as? ArendFile
                    if (arendFile != null) {
                        tasks.add(standardName to LocalSearchScope(arendFile))
                    }
                }
                if (aliasName != null) {
                    collectSearchScopes(listOf(aliasName), scope.isSearchInLibraries, project).forEach {
                        val arendFile = PsiManager.getInstance(project).findFile(it) as? ArendFile
                        if (arendFile != null) {
                            tasks.add(aliasName to LocalSearchScope(arendFile))
                        }
                    }
                }
            } else if (aliasName != null) {
                tasks.add(Pair(aliasName, scope))
            }
            tasks.add(Pair(standardName, scope))
        }

        val searchContext = (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_FOREIGN_LANGUAGES.toInt()).toShort()
        for (task in tasks) {
            parameters.optimizer.searchWord(task.first, task.second, searchContext, true, elementToSearch, object: RequestResultProcessor(){
                override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
                    (element as? ArendNsId)?.defIdentifier?.let { dI ->
                        PsiSearchHelperImpl(project).processElementsWithWord({ element, _ ->
                            !(element is ArendRefIdentifier && element.reference?.isReferenceTo(elementToSearch) == true && !consumer.process(element.reference))
                        }, dI.useScope, dI.name, searchContext, true)
                    }
                    for (ref in PsiReferenceService.getService().getReferences(element, PsiReferenceService.Hints(elementToSearch, offsetInElement))) { // Copypasted from SingleTargetRequestResultProcessor
                        ProgressManager.checkCanceled()
                        if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && ref.isReferenceTo(elementToSearch)) {
                            if (!consumer.process(ref))
                                return false
                        }
                    }
                    return true
                }
            })
        }
    }
}

/**
 * Every returned file contains **all** of identifiers specified in [namesToSearch]
 */
fun collectSearchScopes(namesToSearch: List<String>, isSearchInLibraries: Boolean, project: Project): List<VirtualFile> =
    runReadAction {
        val fileBasedIndex = FileBasedIndex.getInstance()
        val fileSet = HashSet<VirtualFile>()
        fileBasedIndex.getFilesWithKey(
            IdIndex.NAME,
            namesToSearch.mapSmartSet { IdIndexEntry(it, true) },
            Processors.cancelableCollectProcessor(fileSet),
            ArendFileScope(project)
        )
        if (isSearchInLibraries) {
            project.service<ArendServerService>().prelude?.let {
                fileSet.add(it.virtualFile)
            }
        }
        fileSet.toList()
    }