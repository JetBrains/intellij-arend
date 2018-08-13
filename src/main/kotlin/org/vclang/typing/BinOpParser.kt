package org.vclang.typing

import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.naming.BinOpParser
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.Fixity
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.BaseAbstractExpressionVisitor
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.psi.VcExpr


private fun addExpression(expr: Abstract.Expression?, binOpSeq: MutableList<Concrete.BinOpSequenceElem>, fixity: Fixity, isExplicit: Boolean) {
    val ref = expr?.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression?>(null) {
        private fun getResult(data: Any?, referent: Referable): Concrete.Expression? =
            if (expr is VcExpr) {
                val refExpr = Concrete.ReferenceExpression(data, referent, Concrete.PLevelExpression(data), Concrete.HLevelExpression(data))
                val arg = ExpressionResolveNameVisitor.resolve(refExpr, expr.scope)
                if (arg == null) refExpr else Concrete.AppExpression.make(data, refExpr, arg, false)
            } else {
                null
            }

        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?) = getResult(data, referent)

        override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = getResult(data, referent)
    }, null)

    if (ref is Concrete.ReferenceExpression || ref is Concrete.AppExpression && ref.function is Concrete.ReferenceExpression) {
        binOpSeq.add(Concrete.BinOpSequenceElem(ref, fixity, isExplicit))
    } else {
        binOpSeq.add(Concrete.BinOpSequenceElem(Concrete.HoleExpression(expr), fixity, isExplicit))
    }
}

fun parseBinOp(left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression {
    val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem>()
    addExpression(left, concreteSeq, Fixity.NONFIX, true)
    for (elem in sequence) {
        addExpression(elem.expression, concreteSeq, elem.fixity, elem.isExplicit)
    }
    return BinOpParser(DummyErrorReporter.INSTANCE).parse(Concrete.BinOpSequenceExpression(null, concreteSeq))
}

