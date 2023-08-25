package org.arend.intention.binOp

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendIPName
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete
import org.arend.util.isBinOp

object BinOpIntentionUtil {
    internal fun findBinOp(element: PsiElement): ArendReferenceContainer? {
        val binOpReference = PsiTreeUtil.findFirstParent(skipWhiteSpacesBackwards(element)) {
            it is ArendLongName || it is ArendIPName
        } as? ArendReferenceContainer ?: return null
        return if (isBinOp(binOpReference)) binOpReference else null
    }

    internal fun toConcreteBinOpInfixApp(app: ArendArgumentAppExpr): Concrete.AppExpression? {
        val binOpSeq = appExprToConcrete(app, true)
        return if (binOpSeq is Concrete.AppExpression && isBinOpInfixApp(binOpSeq)) binOpSeq else null
    }

    internal fun isBinOpInfixApp(expression: Concrete.AppExpression) =
            isBinOp(expression.function.data as? ArendReferenceContainer) && !isPrefixForm(expression)

    private fun skipWhiteSpacesBackwards(element: PsiElement) =
            if (element is PsiWhiteSpace) PsiTreeUtil.prevCodeLeaf(element) else element

    private fun isPrefixForm(expression: Concrete.AppExpression): Boolean {
        val firstArg = expression.arguments.firstOrNull { it.isExplicit }?.expression ?: return false
        return rangeOfConcrete(expression.function).endOffset <= rangeOfConcrete(firstArg).startOffset
    }
}