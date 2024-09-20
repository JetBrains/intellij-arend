package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.intention.binOp.BinOpSeqProcessor
import org.arend.intention.binOp.CaretHelper
import org.arend.naming.binOp.MetaBinOpParser
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.surroundingTupleExpr
import org.arend.refactoring.unwrapParens
import org.arend.term.concrete.Concrete
import org.arend.typechecking.order.PartialComparator
import org.arend.util.ArendBundle

class RemoveClarifyingParensIntention : BaseArendIntention(ArendBundle.message("arend.expression.removeClarifyingParentheses")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        editor ?: return false
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return false
        val binOpSeqPsi = binOp.parentOfType<ArendArgumentAppExpr>() ?: return false
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpInfixApp(binOpSeqPsi) ?: return false
        val parentBinOp = getParentBinOpSkippingParens(binOpSeq) ?: binOpSeq
        return hasClarifyingParens(parentBinOp)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return
        val binOpSeqPsi = binOp.parentOfType<ArendArgumentAppExpr>() ?: return
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpInfixApp(binOpSeqPsi) ?: return
        val parentBinOp = getParentBinOpSkippingParens(binOpSeq) ?: binOpSeq
        RemoveClarifyingParensProcessor().run(project, editor, binOp, parentBinOp)
    }
}

private fun getParentBinOpSkippingParens(binOp: Concrete.AppExpression): Concrete.AppExpression? {
    val tuple =  (binOp.data as? ArendArgumentAppExpr)?.let{ parentParensExpression(it) } ?: return null
    val parentAppExprPsi = parentArgumentAppExpr(tuple) ?: return null
    if (parentAppExprPsi.argumentList.isEmpty()) {
        return null
    }
    val parentAppExpr = BinOpIntentionUtil.toConcreteBinOpInfixApp(parentAppExprPsi) ?: return null
    return getParentBinOpSkippingParens(parentAppExpr) ?: parentAppExpr
}

private fun parentParensExpression(appExpr: ArendArgumentAppExpr): ArendTuple? =
        surroundingTupleExpr(appExpr)
                ?.let { if (it.colon == null) it.parent as? ArendTuple else null }
                ?.takeIf { it.tupleExprList.size == 1 }

private fun parentArgumentAppExpr(tuple: ArendTuple): ArendArgumentAppExpr? =
        tuple.parentOfType<ArendAtomFieldsAcc>()?.let { parentArgumentAppExpr(it) }

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
            if (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(expression)) {
                queue.add(expression)
            }
        }
    }
    return clarifyingParensFound
}

private fun findBinOpInParens(expression: Concrete.HoleExpression): Concrete.AppExpression? {
    val tuple = (expression.data as? ArendAtomFieldsAcc)?.descendantOfType<ArendTuple>() ?: return null
    val appExprPsi = unwrapAppExprInParens(tuple) ?: return null
    return BinOpIntentionUtil.toConcreteBinOpInfixApp(appExprPsi)
}

private fun unwrapAppExprInParens(tuple: ArendTuple): ArendArgumentAppExpr? {
    val expr = unwrapParens(tuple) ?: return null
    return (expr as? ArendNewExpr)?.appExpr as? ArendArgumentAppExpr ?: return null
}

private fun doesNotNeedParens(childBinOp: Concrete.AppExpression, parentBinOp: Concrete.AppExpression): Boolean {
    val childPrecedence = getPrecedence(childBinOp.function) ?: return false
    val parentPrecedence = getPrecedence(parentBinOp.function) ?: return false
    val childIsLeftArg = rangeOfConcrete(childBinOp).endOffset < rangeOfConcrete(parentBinOp.function).startOffset
    return if (childIsLeftArg)
        MetaBinOpParser.comparePrecedence(childPrecedence, parentPrecedence) == PartialComparator.Result.GREATER
    else MetaBinOpParser.comparePrecedence(parentPrecedence, childPrecedence) == PartialComparator.Result.LESS
}

private fun getPrecedence(function: Concrete.Expression) =
        ((function as? Concrete.ReferenceExpression)?.referent as? GlobalReferable)?.precedence

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
        if (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(expression)) {
            return mapBinOp(expression, editor, caretHelper)
        }
        return text(arg.expression, editor)
    }
}