package org.arend.search

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.psi.parentOfType
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ClassDescendantsSearch(val project: Project) : ArendDefinitionChangeListener {
    private val cache = ConcurrentHashMap<ArendDefinition<*>, List<ArendDefinition<*>>>()

    var FIND_SUBCLASSES = true
        set(value) {
            if (value != field) {
                field = value
                cache.clear()
            }
        }

    var FIND_INSTANCES = true
        set(value) {
            if (value != field) {
                field = value
                cache.clear()
            }
        }

    init {
        service<ArendPsiChangeService>().addListener(this)
    }

    fun search(clazz: ArendDefClass): List<ArendDefinition<*>> {
        if (!FIND_INSTANCES && !FIND_SUBCLASSES) {
            return emptyList()
        }

        var res = cache[clazz]

        if (res != null) {
            return res
        }

        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(clazz, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(project)
        val descendants = HashSet<ArendDefinition<*>>()
        options.isUsages = true
        options.isSearchForTextOccurrences = false

        finder?.processElementUsages(clazz, processor, options)

        for (usage in processor.results) {
             if (FIND_SUBCLASSES) {
                (usage.element?.parent as? ArendLongName)?.let { longName ->
                    (longName.parent as? ArendSuperClass)?.let { superClass ->
                        (superClass.parent as? ArendDefClass)?.let { defClass ->
                            if (longName.refIdentifierList.lastOrNull()?.reference?.resolve() == clazz) {
                                descendants.add(defClass)
                            }
                        }
                    }
                }
            }

            if (FIND_INSTANCES) {
                val defInst = usage.element?.parentOfType<ArendDefInstance>() //ArendReturnExpr>()?.parent as? InstanceAdapter
                if (defInst?.classReference == clazz) {
                    descendants.add(defInst)
                }
            }
        }

        res = descendants.toList()
        return cache.putIfAbsent(clazz, res) ?: res
    }

    fun getAllDescendants(clazz: ArendDefClass): List<ArendDefinition<*>> {
        val visited = mutableSetOf<ArendDefinition<*>>()
        val toBeVisited: MutableSet<ArendDefinition<*>> = mutableSetOf(clazz)

        while (toBeVisited.isNotEmpty()) {
            val newToBeVisited = mutableSetOf<ArendDefinition<*>>()
            for (cur in toBeVisited) {
                if (!visited.contains(cur)) {
                    if (cur is ArendDefClass) {
                        newToBeVisited.addAll(search(cur))
                    }
                    visited.add(cur)
                }
            }
            toBeVisited.clear()
            toBeVisited.addAll(newToBeVisited)
        }

        visited.remove(clazz)
        return visited.toList()
    }

    override fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean) {
        if (def is ArendDefClass && FIND_SUBCLASSES || def is ArendDefInstance && FIND_INSTANCES) {
            cache.clear()
        }
    }
}