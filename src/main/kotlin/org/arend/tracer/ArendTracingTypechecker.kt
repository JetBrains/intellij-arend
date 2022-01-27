package org.arend.tracer

import org.arend.core.expr.*
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.GoalDataHolder
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class ArendTracingTypechecker(errorReporter: ErrorReporter, extension: ArendExtension?) :
    CheckTypeVisitor(errorReporter, null, extension) {

    private val traceEntries: MutableList<ArendTraceEntry> = mutableListOf()
    private val entriesStack: ArrayDeque<ArendTraceEntry> = ArrayDeque()

    val trace: ArendTrace = ArendTrace(traceEntries)

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult? {
        val traceEntry = ArendTraceEntry(entriesStack.lastOrNull())
        traceEntries += traceEntry
        entriesStack.addLast(traceEntry)
        val result = super.checkExpr(expr, expectedType)
        entriesStack.removeLast()
        traceEntry.typecheckingResult = result
        traceEntry.goalDataHolder = GoalDataHolder(
            GeneralError.Level.INFO,
            "",
            expr,
            saveTypecheckingContext(),
            bindingTypes,
            expectedType
        )
        return result
    }
}
