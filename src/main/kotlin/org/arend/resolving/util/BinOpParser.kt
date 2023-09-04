package org.arend.resolving.util

import com.intellij.openapi.components.service
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.ErrorReporter
import org.arend.naming.binOp.ExpressionBinOpEngine
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.Scope
import org.arend.psi.ext.*
import org.arend.resolving.ArendResolveCache
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete


fun resolveReference(data: Any?, referent: Referable, fixity: Fixity?, scope: Scope? = null): Concrete.Expression? {
    return if (data is ArendCompositeElement) {
        val referentData = when (referent) {
            is NamedUnresolvedReference -> referent.data
            is LongUnresolvedReference -> referent.data
            else -> null
        }

        var arg: Concrete.Expression? = null
        val scope1 =  ((data as? ArendIPName)?.parentLiteral ?: data).scope

        val anchor: ArendReferenceElement? = when (referentData) {
            is ArendLongName -> referentData.referenceNameElement
            is ArendIPName -> referentData.referenceNameElement
            is ArendRefIdentifier -> referentData.referenceNameElement
            else -> null
        }

        val resolveCache = data.project.service<ArendResolveCache>()
        val refExpr = Concrete.FixityReferenceExpression.make(data, referent, fixity, null, null)
        val computer = {
            arg = ExpressionResolveNameVisitor.resolve(refExpr, scope1, false, null)
            refExpr.referent
        }

        val referable = if (anchor != null) resolveCache.resolveCached(computer, anchor) else computer.invoke()
        if (arg == null && referent is LongUnresolvedReference && data is ArendLongName) {
            val list = data.refIdentifierList.map { resolveCache.resolveCached(computer, it) }.toMutableList()
            val resolvedRefs = ArrayList<Referable?>()
            var i = 0
            var canResolve = true
            while (i < list.size - 1) {
                val curr = list[i]
                resolvedRefs.add(curr)
                val next = list[i+1]
                if (curr !is ReferableBase<*> || next == null || curr.scope.resolveName(next.refName) == null) {
                    canResolve = false
                    break
                }
                i++
            }
            if (!canResolve) arg = referent.resolveField(scope1, i, false, resolvedRefs)
        }
        if (referable != null) refExpr.referent = referable

        if (arg == null) refExpr else Concrete.AppExpression.make(data, refExpr, arg, false)
    } else {
        null
    }
}

private fun getExpression(expr: Abstract.Expression?, scope: Scope? = null): Concrete.Expression {
    val ref = expr?.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression?>(null) {
        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = resolveReference(data, referent, null, scope)
        override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: Collection<Abstract.LevelExpression>?, hLevels: Collection<Abstract.LevelExpression>?, params: Void?) = resolveReference(data, referent, fixity, scope)    }, null)

    return if (ref is Concrete.ReferenceExpression || ref is Concrete.AppExpression && ref.function is Concrete.ReferenceExpression) ref else Concrete.HoleExpression(expr)
}

fun parseBinOp(left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression =
        parseBinOp(null, left, sequence)

fun parseBinOp(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorReporter: ErrorReporter = DummyErrorReporter.INSTANCE, scope: Scope? = null): Concrete.Expression {
    val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem<Concrete.Expression>>()
    concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(left, scope)))
    for (elem in sequence) {
        concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(elem.expression, scope), if (elem.isVariable) Fixity.UNKNOWN else Fixity.NONFIX, elem.isExplicit))
    }
    return ExpressionBinOpEngine.parse(Concrete.BinOpSequenceExpression(data, concreteSeq, null), errorReporter)
}

/**
 * Attempts to parse abstract expression assuming it is already a bin op sequence.
 */
fun parseBinOp(expr : Abstract.Expression) : Concrete.Expression? {
    var result : Concrete.Expression? = null
    expr.accept(object : BaseAbstractExpressionVisitor<Unit, Nothing?>(null){
        override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, params: Unit?): Nothing? {
            result = parseBinOp(left, sequence)
            return null
        }
    }, Unit)
    return result
}
