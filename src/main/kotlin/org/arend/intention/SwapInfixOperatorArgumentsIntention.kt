package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendExpr
import org.arend.psi.parentOfType
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.findDefAndArgsInParsedBinop

class SwapInfixOperatorArgumentsIntention : BaseArendIntention(ArendBundle.message("arend.expression.swapInfixArguments")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        editor ?: return false
        return findBinOpArguments(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val (left, right) = findBinOpArguments(element) ?: return
        val leftRange = rangeOfConcrete(left.expression)
        val rightRange = rangeOfConcrete(right.expression)
        val leftText = editor.document.getText(leftRange)
        val rightText = editor.document.getText(rightRange)
        editor.document.replaceString(rightRange.startOffset, rightRange.endOffset, leftText)
        editor.document.replaceString(leftRange.startOffset, leftRange.endOffset, rightText)
    }

    private fun findBinOpArguments(element: PsiElement): Pair<Concrete.Argument, Concrete.Argument>? {
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return null
        val binOpExpr = binOp.parentOfType<ArendExpr>() ?: return null
        val binOpSequenceAbs = binOpExpr.parentOfType<ArendArgumentAppExpr>() ?: return null
        val binOpSequence = appExprToConcrete(binOpSequenceAbs) ?: return null
        val (_, _, arguments) = findDefAndArgsInParsedBinop(binOpExpr, binOpSequence) ?: return null
        return arguments.filter { it.isExplicit }.let { if (it.size == 2) it[0] to it[1] else null }
    }
}