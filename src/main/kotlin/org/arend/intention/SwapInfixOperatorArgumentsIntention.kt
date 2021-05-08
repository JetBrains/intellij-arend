package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.arend.error.DummyErrorReporter
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.refactoring.rangeOfConcrete
import org.arend.resolving.ArendReferableConverter
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete
import org.arend.util.findDefAndArgsInParsedBinop

class SwapInfixOperatorArgumentsIntention : BaseArendIntention(NAME) {
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
        val binOpReference = PsiTreeUtil.findFirstParent(skipWhiteSpacesBackwards(element)) {
            it is ArendRefIdentifier || it is ArendIPName
        } as? ArendReferenceElement ?: return null
        val isBinOp =
                if (binOpReference is ArendIPName) binOpReference.infix != null
                else resolveFunction(binOpReference)?.data?.precedence?.isInfix == true
        if (!isBinOp) {
            return null
        }
        val binOp = binOpReference.parentOfType<ArendExpr>() ?: return null
        val binOpSequenceAbs = binOp.parentOfType<ArendArgumentAppExpr>() ?: return null
        val binOpSequence = appExprToConcrete(binOpSequenceAbs) ?: return null
        val (_, _, arguments) = findDefAndArgsInParsedBinop(binOp, binOpSequence) ?: return null
        return arguments.filter { it.isExplicit }.let { if (it.size == 2) it[0] to it[1] else null }
    }

    companion object {
        const val NAME = "Swap infix operator arguments"
    }
}

private fun skipWhiteSpacesBackwards(element: PsiElement) =
        if (element is PsiWhiteSpace) PsiTreeUtil.prevCodeLeaf(element) else element

private fun resolveFunction(referenceNode: ArendReferenceElement): Concrete.FunctionDefinition? {
    val functionAbs = referenceNode.resolve as? Abstract.FunctionDefinition ?: return null
    return ConcreteBuilder.convert(ArendReferableConverter, functionAbs, DummyErrorReporter.INSTANCE)
            as? Concrete.FunctionDefinition ?: return null
}