package org.arend.tracer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.castSafelyTo
import org.arend.core.expr.Expression
import org.arend.psi.linearDescendants
import org.arend.typechecking.error.local.GoalDataHolder
import org.arend.typechecking.result.TypecheckingResult

class ArendTrace(val entries: List<ArendTraceEntry>){
    fun indexOfEntry(element: PsiElement): Int {
        val normalizedElement = element.linearDescendants.lastOrNull() ?: element
        return entries.withIndex()
            .map {
                ProgressManager.checkCanceled()
                it.index to PsiTreeUtil.getDepth(normalizedElement, it.value.psiElement)
            }
            .minByOrNull { it.second }
            ?.first
            ?: -1
    }
}

class ArendTraceEntry(val parent: ArendTraceEntry? = null) {
    var typecheckingResult: TypecheckingResult? = null
    lateinit var goalDataHolder: GoalDataHolder

    val coreExpression: Expression?
        get() = typecheckingResult?.expression
    val psiElement: PsiElement?
        get() = goalDataHolder.cause?.data?.castSafelyTo<PsiElement>()

    val stack: List<ArendTraceEntry> by lazy {
        generateSequence(this) { it.parent }.toList()
    }
}

class ArendTracingData(val trace: ArendTrace, val hasErrors: Boolean, val firstEntryIndex: Int)