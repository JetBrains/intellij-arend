package org.arend.resolving.util

import com.intellij.psi.PsiElement
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.ErrorReporter
import org.arend.naming.binOp.ExpressionBinOpEngine
import org.arend.naming.reference.*
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.naming.scope.Scope
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.BaseAbstractExpressionVisitor
import org.arend.term.concrete.Concrete


fun resolveReference(data: Any?, referent: Referable, fixity: Fixity?): Concrete.Expression? {
    return if (data is ArendCompositeElement) {
        val referentData = when (referent) {
            is NamedUnresolvedReference -> referent.data
            is LongUnresolvedReference -> referent.data
            else -> null
        }

        val scope1 =  ((data as? ArendIPName)?.parentLiteral ?: data).scope

        val anchor: ArendReferenceElement? = when (referentData) {
            is ArendLongName -> referentData.referenceNameElement
            is ArendIPName -> referentData.referenceNameElement
            is ArendRefIdentifier -> referentData.referenceNameElement
            else -> null
        }

        val refExpr = Concrete.FixityReferenceExpression.make(data, referent, fixity, null, null)

        var resolved: Concrete.Expression? = null
        var isCachedValue = true
        val referentComputer = {
            isCachedValue = false
            resolved = null // TODO[server2]: ExpressionResolveNameVisitor.resolve(refExpr, scope1, false, null, null)
            refExpr.referent
        }

        var referable = if (anchor != null) anchor.cachedReferable else referentComputer.invoke()

        if (fixity == Fixity.POSTFIX)
            resolved = null
        else {
            val isDynamicClassMember = (referable as? PsiElement)?.ancestor<ArendDefClass>()?.dynamicReferables?.contains(referable) == true
            if (isDynamicClassMember && isCachedValue) referentComputer.invoke()
        }

        if (resolved == null && referent is LongUnresolvedReference && data is ArendLongName) {
            val referable1 = RedirectingReferable.getOriginalReferable(refExpr.referent)
            val resolvedRefs = data.refIdentifierList.map { it.resolve as? Referable }.toMutableList()
            if (referable1 is UnresolvedReference) {
                resolved = referable1.resolveExpression(scope1, TypingInfo.EMPTY, resolvedRefs, null)
                referable = referable1.resolve(scope1, null, null)
            }
        }
        if (referable != null) refExpr.referent = referable
        if (resolved == null) refExpr else resolved
    } else {
        null
    }
}

private fun getExpression(expr: Abstract.Expression?): Concrete.Expression {
    val ref = expr?.accept(object : BaseAbstractExpressionVisitor<Void, Concrete.Expression?>(null) {
        override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = resolveReference(data, referent, null)
        override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: Collection<Abstract.LevelExpression>?, hLevels: Collection<Abstract.LevelExpression>?, params: Void?) = resolveReference(data, referent, fixity)    }, null)

    return if (ref is Concrete.ReferenceExpression || ref is Concrete.AppExpression && ref.function is Concrete.ReferenceExpression) ref else Concrete.HoleExpression(expr)
}

fun parseBinOp(left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>): Concrete.Expression =
        parseBinOp(null, left, sequence)

fun parseBinOp(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorReporter: ErrorReporter = DummyErrorReporter.INSTANCE, scope: Scope? = null): Concrete.Expression {
    val concreteSeq = mutableListOf<Concrete.BinOpSequenceElem<Concrete.Expression>>()
    concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(left)))
    for (elem in sequence) {
        concreteSeq.add(Concrete.BinOpSequenceElem(getExpression(elem.expression), if (elem.isVariable) Fixity.UNKNOWN else Fixity.NONFIX, elem.isExplicit))
    }
    return ExpressionBinOpEngine.parse(Concrete.BinOpSequenceExpression(data, concreteSeq, null), errorReporter, TypingInfo.EMPTY)
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
