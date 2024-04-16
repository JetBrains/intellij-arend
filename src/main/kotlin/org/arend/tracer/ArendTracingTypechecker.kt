package org.arend.tracer

import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.ArendExtension
import org.arend.ext.error.ErrorReporter
import org.arend.extImpl.userData.UserDataHolderImpl
import org.arend.naming.reference.Referable
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.LocalExpressionPrettifier
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.visitor.CheckTypeVisitor

class ArendTracingTypechecker private constructor(localContext: MutableMap<Referable, Binding>?, localPrettifier: LocalExpressionPrettifier, errorReporter: ErrorReporter?, pool: GlobalInstancePool?, arendExtension: ArendExtension?, holder: UserDataHolderImpl?) :
    CheckTypeVisitor(localContext, localPrettifier, errorReporter, pool, arendExtension, holder) {

    constructor(errorReporter: ErrorReporter, extension: ArendExtension?)
        : this(LinkedHashMap<Referable, Binding>(), LocalExpressionPrettifier(), errorReporter, null, extension, null)

    private val traceEntries: MutableList<ArendTraceEntry> = mutableListOf()
    private val entriesStack: ArrayDeque<ArendTraceEntry> = ArrayDeque()

    val trace: ArendTrace = ArendTrace(traceEntries)

    override fun copy(localContext: MutableMap<Referable, Binding>?, localPrettifier: LocalExpressionPrettifier, errorReporter: ErrorReporter?, pool: GlobalInstancePool?, arendExtension: ArendExtension?, holder: UserDataHolderImpl?): CheckTypeVisitor {
        return ArendTracingTypechecker(localContext, localPrettifier, errorReporter, pool, arendExtension, holder)
    }

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?): TypecheckingResult? {
        val traceEntry = ArendTraceEntry(entriesStack.lastOrNull())
        traceEntries += traceEntry
        entriesStack.addLast(traceEntry)
        val result = super.checkExpr(expr, expectedType)
        entriesStack.removeLast()
        traceEntry.typecheckingResult = result
        traceEntry.goalDataHolder = ArendTraceSyntheticError(expressionPrettifier, expr, saveTypecheckingContext(), bindingTypes, result?.expression, expectedType)
        return result
    }
}
