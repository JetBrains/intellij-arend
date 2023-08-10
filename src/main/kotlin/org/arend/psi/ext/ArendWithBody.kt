package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfTypeStrict
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.Abstract

class ArendWithBody(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.FunctionClauses {
    val withKw: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.WITH_KW)

    val lbrace: PsiElement?
        get() = findChildByType(ArendElementTypes.LBRACE)

    override fun getData() = this

    override fun getClauseList(): List<ArendClause> = getChildrenOfType()
}