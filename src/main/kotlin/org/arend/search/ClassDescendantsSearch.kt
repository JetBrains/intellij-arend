package org.arend.search

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.ext.TCDefinition
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendDefinitionChangeService
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typing.parseBinOp
import org.arend.util.checkConcreteExprIsFunc
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp

class ClassDescendantsSearch(val project: Project) : ArendDefinitionChangeListener {
    private val cache = ConcurrentHashMap<ArendDefinition, List<ArendDefinition>>()

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
        project.service<ArendDefinitionChangeService>().addListener(this)
    }

    fun search(clazz: ArendDefClass): List<ArendDefinition> {
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
        val descendants = HashSet<ArendDefinition>()
        options.isUsages = true
        options.isSearchForTextOccurrences = false

        finder?.processElementUsages(clazz, processor, options)

        for (usage in processor.results) {
            if (FIND_SUBCLASSES) {
                (usage.element?.parent as? ArendLongName)?.let { longName ->
                    (longName.parent as? ArendDefClass)?.let { defClass ->
                        if (longName.refIdentifierList.lastOrNull()?.reference?.resolve() == clazz) {
                            descendants.add(defClass)
                        }
                    }
                }
            }

            if (FIND_INSTANCES) {
                val returnExpr = usage.element?.parentOfType<ArendReturnExpr>() ?: continue
                val defInst = returnExpr.parent as? ArendDefInstance ?: continue

                if (returnExpr.atomFieldsAccList.isNotEmpty()) {
                    if (usage.element?.parentOfType<ArendAtomFieldsAcc>() == returnExpr.atomFieldsAccList[0]) {
                        val longName = usage.element?.parentOfType<ArendLongName>() ?: continue
                        if (longName.refIdentifierList.lastOrNull()?.reference?.resolve() == clazz) {
                            descendants.add(defInst)
                        }
                    }
                    continue
                }

                if (returnExpr.exprList.isNotEmpty()) {
                    val appExpr = (returnExpr.exprList[0] as? ArendNewExpr)?.appExpr ?: continue
                    val refDef = extractTopLevelFunc(appExpr)
                    if (refDef == clazz) {
                        descendants.add(defInst)
                    }
                }
            }
        }

        res = descendants.toList()
        return cache.putIfAbsent(clazz, res) ?: res
    }

    private fun extractTopLevelFunc (appExpr: ArendAppExpr): PsiElement?  {
        return appExpr.accept(object: BaseAbstractExpressionVisitor<Void, PsiElement?>(null) {
            override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?): PsiElement? {
                val expr = parseBinOp(left, sequence)
                if (checkConcreteExprIsFunc(expr, appExpr.scope)) return (expr.data as? ArendReferenceContainer)?.resolve
                if (expr is Concrete.AppExpression) {
                    if (checkConcreteExprIsFunc(expr.function, appExpr.scope)) return (expr.function.data as? ArendReferenceContainer)?.resolve
                }
                return null
            }

            override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?): PsiElement? {
                if (referent is UnresolvedReference) return referent.tryResolve(appExpr.scope) as? PsiElement
                return referent as? PsiElement
            }

            override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?): PsiElement? {
                if (referent is UnresolvedReference) return referent.tryResolve(appExpr.scope) as? PsiElement
                return referent as? PsiElement
            }
        }, null)
    }

    fun getAllDescendants(clazz: ArendDefClass): List<ArendDefinition> {
        val visited = mutableSetOf<ArendDefinition>()
        val toBeVisited: MutableSet<ArendDefinition> = mutableSetOf(clazz)

        while (toBeVisited.isNotEmpty()) {
            val newToBeVisited = mutableSetOf<ArendDefinition>()
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

    override fun updateDefinition(def: TCDefinition, file: ArendFile, isExternalUpdate: Boolean) {
        if (def is ArendDefClass && FIND_SUBCLASSES || def is ArendDefInstance && FIND_INSTANCES) {
            cache.clear()
        }
    }
}