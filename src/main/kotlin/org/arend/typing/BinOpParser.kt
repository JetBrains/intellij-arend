package org.arend.typing

import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ArendLongName
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete


fun resolveReference(data: Any?, referent: Referable) =
    if (data is ArendCompositeElement) {
        if (data !is ArendLongName || data.refIdentifierList.size <= 1) {
            Concrete.ReferenceExpression(data, ((data as? ArendLongName)?.refIdentifierList?.lastOrNull() ?: data).reference?.resolve() as? Referable ?: ErrorReference(data, referent.textRepresentation()))
        } else {
            val refExpr = Concrete.ReferenceExpression(data, referent)
            val arg = ExpressionResolveNameVisitor.resolve(refExpr, data.scope, null)
            (refExpr.referent as? GlobalReferable)?.let {
                val psiRef = PsiLocatedReferable.fromReferable(it)
                if (psiRef != null) {
                    refExpr.referent = psiRef
                }
            }
            if (arg == null) refExpr else Concrete.AppExpression.make(data, refExpr, arg, false)
        }
    } else {
        null
    }

private fun addExpression(expr: Abstract.Expression?, binOpSeq: MutableList<Concrete.BinOpSequenceElem>, fixity: Fixity, isExplicit: Boolean) {
    val ref = expr?.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression?>(null) {
        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, params: Void?) = resolveReference(data, referent)
        override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, params: Void?) = resolveReference(data, referent)
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

