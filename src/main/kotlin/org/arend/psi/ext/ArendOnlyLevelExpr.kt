package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfType
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractLevelExpressionVisitor


open class ArendOnlyLevelExpr(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.LevelExpression {
    fun getAtomLevelExpr(index: Int): ArendAtomLevelExpr? = childOfType(index)

    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R =
        when (firstRelevantChild.elementType) {
            ArendElementTypes.SUC_KW -> visitor.visitSuc(this, getAtomLevelExpr(0), params)
            ArendElementTypes.MAX_KW -> visitor.visitMax(this, getAtomLevelExpr(0), getAtomLevelExpr(1), params)
            else -> error("Incomplete expression: $this")
        }
}
