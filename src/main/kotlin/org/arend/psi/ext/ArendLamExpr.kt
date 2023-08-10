package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor

class ArendLamExpr(node: ASTNode) : ArendExpr(node), Abstract.LamParametersHolder {
    val lamParamList: List<ArendLamParam>
        get() = getChildrenOfType()

    val body: ArendExpr?
        get() = childOfType()

    val fatArrow: PsiElement?
        get() = findChildByType(ArendElementTypes.FAT_ARROW)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLam(this, lamParamList, body, params)

    override fun getParameters() = lamParamList.filterIsInstance<ArendNameTele>()

    override fun getLamParameters() = lamParamList
}
