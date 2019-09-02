package org.arend.util

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendLongName
import org.arend.psi.listener.ArendPsiListener
import org.arend.psi.listener.ArendPsiListenerService
import java.util.concurrent.ConcurrentHashMap

class ClassInheritorsSearch(val project: Project) {
    private val cache = ConcurrentHashMap<ArendDefClass, List<ArendDefClass>>()

    init {
        project.service<ArendPsiListenerService>().addListener(ClassesChangedListener())
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
            if (usage.element?.parent is ArendLongName && usage.element?.parent?.parent is ArendDefClass) {
                val parentLongName = usage.element?.parent as ArendLongName
                if (parentLongName.refIdentifierList.last().text == clazz.name) {
                    val subclass = usage.element?.parent?.parent as ArendDefClass

                    subClasses.add(subclass)

                }
            }
        }

        res = subClasses.toList()
        cache[clazz] = res

        return res
    }

    private inner class ClassesChangedListener : ArendPsiListener() {
        override fun updateDefinition(def: ArendDefinition) {
            if (def is ArendDefClass) {
                cache.clear()
            }
        }

    }

    companion object {
        fun getInstance(project: Project) = ClassInheritorsSearch(project)
    }
}