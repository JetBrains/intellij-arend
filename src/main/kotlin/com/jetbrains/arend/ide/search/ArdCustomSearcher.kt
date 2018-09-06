package com.jetbrains.arend.ide.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.ProjectScopeImpl
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.indexing.FileBasedIndex
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import gnu.trove.THashSet
import java.util.*

class ArdCustomSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val elementToSearch = parameters.elementToSearch
        val scope = parameters.scopeDeterminedByUser
        val fileBasedIndex = FileBasedIndex.getInstance()
        val project = parameters.project

        if (scope is ProjectScopeImpl && elementToSearch is GlobalReferable)
            ApplicationManager.getApplication().runReadAction({
                val name = elementToSearch.textRepresentation()
                val indexEntry = IdIndexEntry(name, true)
                val fileSet = THashSet<VirtualFile>()

                fileBasedIndex.getFilesWithKey(IdIndex.NAME, Collections.singleton(indexEntry), Processors.cancelableCollectProcessor(fileSet), scope)

                fileSet.mapNotNull { PsiManager.getInstance(project).findFile(it) }
                        .filter { it is ArdFile }
                        .forEach { parameters.optimizer.searchWord(name, LocalSearchScope(it), true, elementToSearch) }
            })
    }

}