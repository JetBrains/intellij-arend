package org.arend.tracer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.expr.Expression
import org.arend.highlight.BasePass
import org.arend.psi.linearDescendants
import org.arend.typechecking.error.local.GoalDataHolder
import org.arend.typechecking.result.TypecheckingResult

class ArendTrace(val entries: List<ArendTraceEntry>) {
    fun indexOfEntry(element: PsiElement): Int {
        val normalizedElement = element.linearDescendants.lastOrNull() ?: element
        return entries.withIndex()
            .map {
                ProgressManager.checkCanceled()
                it.index to getDepth(normalizedElement, it.value.psiElement)
            }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
            ?.first
            ?: -1
    }

    companion object {
        private fun getDepth(normalizedElement: PsiElement, ancestor: PsiElement?) =
            if (PsiTreeUtil.isAncestor(ancestor, normalizedElement, false))
                PsiTreeUtil.getDepth(normalizedElement, ancestor)
            else -1
    }
}

class ArendTraceEntry(val parent: ArendTraceEntry? = null) {
    var typecheckingResult: TypecheckingResult? = null
    lateinit var goalDataHolder: GoalDataHolder

    val coreExpression: Expression?
        get() = typecheckingResult?.expression
    val psiElement: PsiElement?
        get() = BasePass.getCauseElement(goalDataHolder.cause?.data)

    val stack: List<ArendTraceEntry> by lazy {
        generateSequence(this) { it.parent }.toList()
    }
}

class ArendTracingData(val trace: ArendTrace, val hasErrors: Boolean, val firstEntryIndex: Int)