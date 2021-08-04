package org.arend.resolving.util

import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ArendLongName
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendIPNameImplMixin
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete


private fun isShort(element: ArendCompositeElement) =
        (element !is ArendLongName || element.refIdentifierList.size == 1) && (element !is ArendIPNameImplMixin || element.parentLongName == null)

fun resolveReference(data: Any?, referent: Referable, fixity: Fixity?) =
        if (data is ArendCompositeElement) {
            if (isShort(data)) {
                Concrete.FixityReferenceExpression.make(data, ((data as? ArendLongName)?.refIdentifierList?.lastOrNull() ?: data).reference?.resolve() as? Referable ?: ErrorReference(data, referent.textRepresentation()), fixity, null, null)
            } else {
                val refExpr = Concrete.FixityReferenceExpression.make(data, referent, fixity, null, null)
                val arg = ExpressionResolveNameVisitor.resolve(refExpr, ((data as? ArendIPNameImplMixin)?.parentLiteral ?: data).scope, true, null)
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

private fun getExpression(expr: Abstract.Expression?): Concrete.Expression {
    val ref = expr?.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression?>(null) {
        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = resolveReference(data, referent, null)
        override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, params: Void?) = resolveReference(data, referent, fixity)
    }, null)

    return if (ref is Concrete.ReferenceExpression || ref is Concrete.AppExpression && ref.function is Concrete.ReferenceExpression) ref else Concrete.HoleExpression(expr)
}

fun parseBinOp(left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression =
        parseBinOp(null, left, sequence)

fun parseBinOp(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression {
    val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem>()
    concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(left)))
    for (elem in sequence) {
        concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(elem.expression), if (elem.isVariable) Fixity.UNKNOWN else Fixity.NONFIX, elem.isExplicit))
    }
    return BinOpParser(DummyErrorReporter.INSTANCE).parse(Concrete.BinOpSequenceExpression(data, concreteSeq, null))
}

