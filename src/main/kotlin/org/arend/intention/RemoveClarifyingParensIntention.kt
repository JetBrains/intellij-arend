package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.ext.reference.Precedence
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.intention.binOp.BinOpSeqProcessor
import org.arend.intention.binOp.CaretHelper
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.surroundingTupleExpr
import org.arend.term.concrete.Concrete
import org.arend.typechecking.order.PartialComparator
import org.arend.util.ArendBundle

class RemoveClarifyingParensIntention : BaseArendIntention(ArendBundle.message("arend.expression.removeClarifyingParentheses")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        editor ?: return false
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return false
        val binOpSeqPsi = binOp.parentOfType<ArendArgumentAppExpr>() ?: return false
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpApp(binOpSeqPsi) ?: return false
        val parentBinOp = getParentBinOpSkippingParens(binOpSeq) ?: binOpSeq
        return hasClarifyingParens(parentBinOp)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return
        val binOpSeqPsi = binOp.parentOfType<ArendArgumentAppExpr>() ?: return
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpApp(binOpSeqPsi) ?: return
        val parentBinOp = getParentBinOpSkippingParens(binOpSeq) ?: binOpSeq
        RemoveClarifyingParensProcessor().run(project, editor, binOp, parentBinOp)
    }
}

private fun getParentBinOpSkippingParens(binOp: Concrete.AppExpression): Concrete.AppExpression? {
    val binOpPsi = binOp.data as? ArendArgumentAppExpr ?: throw IllegalArgumentException("Unexpected PSI for bin op")
    val tuple = parentParensExpression(binOpPsi) ?: return null
    val arg = parentArgumentExpression(tuple) ?: return null
    val parentAppExprPsi = arg.parent as? ArendArgumentAppExpr ?: return null
    if (parentAppExprPsi.argumentList.isEmpty()) {
        return null
    }
    val parentAppExpr = BinOpIntentionUtil.toConcreteBinOpApp(parentAppExprPsi) ?: return null
    return getParentBinOpSkippingParens(parentAppExpr) ?: parentAppExpr
}

private fun parentParensExpression(appExpr: ArendArgumentAppExpr): ArendTuple? {
    return surroundingTupleExpr(appExpr)
            ?.let { if (it.colon == null && it.exprList.size == 1) it.parent as? ArendTuple else null }
            ?.let { if (it.tupleExprList.size == 1) it else null }
}

private fun parentArgumentExpression(tuple: ArendTuple): PsiElement? {
    return tuple.parentOfType<ArendAtomFieldsAcc>()?.let {
        if (it.parent is ArendAtomArgument) it.parent else it
    }
}

private fun hasClarifyingParens(binOpSeq: Concrete.AppExpression): Boolean {
    if ((binOpSeq.data as? PsiElement)?.textContains('(') == false) {
        return false
    }
    var clarifyingParensFound = false
    val queue = mutableListOf(binOpSeq)
    while (!clarifyingParensFound && queue.isNotEmpty()) {
        val parentBinOpApp = queue.removeFirst()
        for (arg in parentBinOpApp.arguments) {
            if (!arg.isExplicit) {
                continue
            }
            val expression = arg.expression
            if (expression is Concrete.HoleExpression) {
                val binOp = findBinOpInParens(expression)
                if (binOp != null) {
                    if (doesNotNeedParens(binOp, parentBinOpApp)) {
                        clarifyingParensFound = true
                        break
                    }
                    queue.add(binOp)
                }
            }
            if (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpApp(expression)) {
                queue.add(expression)
            }
        }
    }
    return clarifyingParensFound
}

private fun findBinOpInParens(expression: Concrete.HoleExpression): Concrete.AppExpression? {
    val tuple = (expression.data as? ArendAtomFieldsAcc)?.childOfType<ArendTuple>() ?: return null
    val appExprPsi = unwrapAppExprInParens(tuple) ?: return null
    return BinOpIntentionUtil.toConcreteBinOpApp(appExprPsi)
}

private fun unwrapAppExprInParens(tuple: ArendTuple): ArendArgumentAppExpr? {
    val expr = unwrapParens(tuple) ?: return null
    return (expr as? ArendNewExpr)?.appExpr as? ArendArgumentAppExpr ?: return null
}

private fun unwrapParens(tuple: ArendTuple): ArendExpr? {
    val tupleExpr = tuple.tupleExprList.singleOrNull() ?: return null
    return if (tupleExpr.colon == null) tupleExpr.exprList.singleOrNull() else null
}

private fun doesNotNeedParens(childBinOp: Concrete.AppExpression, parentBinOp: Concrete.AppExpression): Boolean {
    val childPrecedence = getPrecedence(childBinOp.function) ?: return false
    val parentPrecedence = getPrecedence(parentBinOp.function) ?: return false
    val childIsLeftArg = rangeOfConcrete(childBinOp).endOffset < rangeOfConcrete(parentBinOp.function).startOffset
    return if (childIsLeftArg)
        comparePrecedence(childPrecedence, parentPrecedence) == PartialComparator.Result.GREATER
    else comparePrecedence(parentPrecedence, childPrecedence) == PartialComparator.Result.LESS
}

private fun getPrecedence(function: Concrete.Expression) =
        ((function as? Concrete.ReferenceExpression)?.referent as? GlobalReferable)?.precedence

// TODO copy-pasted from MetaBinOpParser
private fun comparePrecedence(prec1: Precedence, prec2: Precedence): PartialComparator.Result = when {
    prec1.priority < prec2.priority -> PartialComparator.Result.LESS
    prec1.priority > prec2.priority -> PartialComparator.Result.GREATER
    prec1.associativity != prec2.associativity || prec1.associativity == Precedence.Associativity.NON_ASSOC ->
        PartialComparator.Result.UNCOMPARABLE
    prec1.associativity == Precedence.Associativity.LEFT_ASSOC -> PartialComparator.Result.GREATER
    else -> PartialComparator.Result.LESS
}

class RemoveClarifyingParensProcessor : BinOpSeqProcessor() {
    override fun mapArgument(arg: Concrete.Argument,
                             parentBinOp: Concrete.AppExpression,
                             editor: Editor,
                             caretHelper: CaretHelper): String? {
        if (!arg.isExplicit) {
            return implicitArgumentText(arg, editor)
        }
        val expression = arg.expression
        if (expression is Concrete.HoleExpression) {
            val binOp = findBinOpInParens(expression)
            if (binOp != null) {
                val binOpText = mapBinOp(binOp, editor, caretHelper)
                return if (doesNotNeedParens(binOp, parentBinOp)) binOpText else "($binOpText)"
            }
        }
        if (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpApp(expression)) {
            return mapBinOp(expression, editor, caretHelper)
        }
        return text(arg.expression, editor)
    }
}