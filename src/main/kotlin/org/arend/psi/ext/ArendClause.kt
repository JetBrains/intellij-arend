package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.term.abs.Abstract
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType


class ArendClause(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.FunctionClause {
    val fatArrow: PsiElement?
        get() = findChildByType(ArendElementTypes.FAT_ARROW)

    override fun getData() = this

    override fun getPatterns(): List<ArendPattern> = getChildrenOfType()

    override fun getExpression(): ArendExpr? = childOfType()
}