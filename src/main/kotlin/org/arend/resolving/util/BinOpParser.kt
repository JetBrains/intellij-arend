package org.arend.resolving.util

import org.arend.error.DummyErrorReporter
import org.arend.ext.error.ErrorReporter
import org.arend.naming.binOp.ExpressionBinOpEngine
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendIPName
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete


fun resolveReference(data: Any?, referent: Referable, fixity: Fixity?) =
        if (data is ArendCompositeElement) {
            val refExpr = Concrete.FixityReferenceExpression.make(data, referent, fixity, null, null)
            val arg = ExpressionResolveNameVisitor.resolve(refExpr, ((data as? ArendIPName)?.parentLiteral ?: data).scope, false, null)
            if (arg == null) refExpr else Concrete.AppExpression.make(data, refExpr, arg, false)
        } else {
            null
        }

private fun getExpression(expr: Abstract.Expression?): Concrete.Expression {
    val ref = expr?.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression?>(null) {
        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = resolveReference(data, referent, null)
        override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: Collection<Abstract.LevelExpression>?, hLevels: Collection<Abstract.LevelExpression>?, params: Void?) = resolveReference(data, referent, fixity)
    }, null)

    return if (ref is Concrete.ReferenceExpression || ref is Concrete.AppExpression && ref.function is Concrete.ReferenceExpression) ref else Concrete.HoleExpression(expr)
}

fun parseBinOp(left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression =
        parseBinOp(null, left, sequence)

fun parseBinOp(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorReporter: ErrorReporter = DummyErrorReporter.INSTANCE): Concrete.Expression {
    val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem<Concrete.Expression>>()
    concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(left)))
    for (elem in sequence) {
        concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(elem.expression), if (elem.isVariable) Fixity.UNKNOWN else Fixity.NONFIX, elem.isExplicit))
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
