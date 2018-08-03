package org.vclang.typing

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.naming.reference.ErrorReference
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.ext.VcCompositeElement
import java.math.BigInteger


class TypecheckingVisitor(private val element: VcCompositeElement, private val holder: AnnotationHolder) : AbstractExpressionVisitor<Any,Unit> {
    override fun visitErrors() = false

    override fun visitApp(data: Any?, expr: Abstract.Expression, arguments: Collection<Abstract.Argument>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitReference(data: Any?, referent: Referable, level1: Abstract.LevelExpression?, level2: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        if (level1 != null || level2 != null) {
            checkIsGlobal(referent)
        }
        // TODO: Check expected type
    }

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, errorData: Abstract.ErrorData?, expectedType: Any?) {
        checkIsGlobal(referent)
        // TODO: Check expected type
    }

    private fun checkIsGlobal(referent: Referable) {
        val ref = ExpressionResolveNameVisitor.resolve(referent, element.scope)
        if (ref !is ErrorReference && ref !is GlobalReferable) {
            holder.createErrorAnnotation(element, "Levels are allowed only after definitions")
        }
    }

    override fun visitLam(data: Any?, parameters: Collection<Abstract.Parameter>, body: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitInferHole(data: Any?, errorData: Abstract.ErrorData?, expectedType: Any?) {}

    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {
        holder.createAnnotation(HighlightSeverity.WARNING, element.textRange, "Goal", "Goal" + if (expectedType != null) "<br>Expected type: " + (if (expectedType is PsiElement) expectedType.text else expectedType.toString()) else "")
    }

    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitCase(data: Any?, expressions: Collection<Abstract.Expression>, clauses: Collection<Abstract.FunctionClause>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Try to infer the type of expressions and check that constructors in clauses have correct types.
        //       If cannot infer the type, check that constructors have the same type.
    }

    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: Collection<Int>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitClassExt(data: Any?, isNew: Boolean, baseClass: Abstract.Expression?, implementations: Collection<Abstract.ClassFieldImpl>?, sequence: Collection<Abstract.BinOpSequenceElem>, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }

    override fun visitLet(data: Any?, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, errorData: Abstract.ErrorData?, expectedType: Any?) {}

    override fun visitNumericLiteral(data: Any?, number: BigInteger, errorData: Abstract.ErrorData?, expectedType: Any?) {
        // TODO: Check expected type
    }
}