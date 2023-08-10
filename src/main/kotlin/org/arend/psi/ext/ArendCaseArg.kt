package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract


class ArendCaseArg(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.CaseArgument {
    val colon: PsiElement?
        get() = findChildByType(COLON)

    val asKw: PsiElement?
        get() = findChildByType(AS_KW)

    val elimKw: PsiElement?
        get() = findChildByType(ELIM_KW)

    override fun getApplyHoleData(): PsiElement? = findChildByType(APPLY_HOLE)

    override fun getExpression(): ArendExpr? = childOfType()

    override fun getReferable(): ArendDefIdentifier? = childOfType()

    override fun getType(): ArendExpr? = colon?.findNextSibling() as? ArendExpr

    override fun getEliminatedReference(): ArendRefIdentifier? = childOfType()
}