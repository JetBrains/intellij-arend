package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfType
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractLevelExpressionVisitor


class ArendAtomOnlyLevelExpr(node: ASTNode) : ArendOnlyLevelExpr(node), ArendTopLevelLevelExpr, Abstract.LevelExpression {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R =
        when (firstRelevantChild.elementType) {
            LP_KW -> visitor.visitLP(this, params)
            LH_KW -> visitor.visitLH(this, params)
            OO_KW -> visitor.visitInf(this, params)
            else -> {
                val child = childOfType<ArendOnlyLevelExpr>()
                if (child != null) child.accept(visitor, params) else visitor.visitError(this, params)
            }
        }
}
