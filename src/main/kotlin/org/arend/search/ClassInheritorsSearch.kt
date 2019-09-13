package org.arend.search

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ArendLongName
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendDefinitionChangeListenerService
import java.util.concurrent.ConcurrentHashMap

class ClassInheritorsSearch(val project: Project) : ArendDefinitionChangeListener {
    private val cache = ConcurrentHashMap<ArendDefClass, List<ArendDefClass>>()

    init {
        project.service<ArendDefinitionChangeListenerService>().addListener(this)
    }

    fun search(clazz: ArendDefClass): List<ArendDefClass> {
        var res = cache[clazz]

        if (res != null) {
            return res
        }

        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(clazz, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(project)
        val subClasses = HashSet<ArendDefClass>()
        options.isUsages = true
        options.isSearchForTextOccurrences = false

        finder?.processElementUsages(clazz, processor, options)

        for (usage in processor.results) {
            (usage.element?.parent as? ArendLongName)?.let { longName ->
                (longName.parent as? ArendDefClass)?.let { defClass ->
                    if (longName.refIdentifierList.lastOrNull()?.reference?.resolve() == clazz) {
                        subClasses.add(defClass)
                    }
                }
            }
        }

        res = subClasses.toList()
        return cache.putIfAbsent(clazz, res) ?: res
    }

    override fun updateDefinition(def: ArendDefinition, file: ArendFile, isExternalUpdate: Boolean) {
        if (def is ArendDefClass) {
            cache.clear()
        }
    }
}