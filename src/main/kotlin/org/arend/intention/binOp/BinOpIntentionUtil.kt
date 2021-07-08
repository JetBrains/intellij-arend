package org.arend.intention.binOp

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.castSafelyTo
import org.arend.naming.reference.AliasReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendIPName
import org.arend.psi.ArendLongName
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete

object BinOpIntentionUtil {
    internal fun findBinOp(element: PsiElement): ArendReferenceContainer? {
        val binOpReference = PsiTreeUtil.findFirstParent(skipWhiteSpacesBackwards(element)) {
            it is ArendLongName || it is ArendIPName
        } as? ArendReferenceContainer ?: return null
        return if (isBinOp(binOpReference)) binOpReference else null
    }

    internal fun toConcreteBinOpApp(app: ArendArgumentAppExpr): Concrete.AppExpression? {
        val binOpSeq = appExprToConcrete(app, true)
        return if (binOpSeq is Concrete.AppExpression && isBinOpApp(binOpSeq)) binOpSeq else null
    }

    internal fun isBinOpApp(expression: Concrete.AppExpression) =
            isBinOp(expression.function.data as? ArendReferenceContainer)

    fun isBinOp(binOpReference: ArendReferenceContainer?) =
            if (binOpReference is ArendIPName) binOpReference.infix != null
            else (resolve(binOpReference))?.precedence?.isInfix == true

    private fun skipWhiteSpacesBackwards(element: PsiElement) =
            if (element is PsiWhiteSpace) PsiTreeUtil.prevCodeLeaf(element) else element

    private fun resolve(reference: ArendReferenceContainer?): GlobalReferable? =
            reference?.resolve?.castSafelyTo<GlobalReferable>()
                    ?.let { if (it.hasAlias() && it.aliasName == reference.referenceName) AliasReferable(it) else it }
}