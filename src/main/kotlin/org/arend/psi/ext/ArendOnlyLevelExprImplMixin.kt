package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendOnlyLevelExpr
import org.arend.term.abs.AbstractLevelExpressionVisitor


abstract class ArendOnlyLevelExprImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendOnlyLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R =
        when {
            sucKw != null -> visitor.visitSuc(this, atomLevelExprList.firstOrNull(), params)
            maxKw != null -> {
                val levelExprs = atomLevelExprList
                visitor.visitMax(this, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
            }
            else -> {
                val lp = atomOnlyLevelExpr ?: error("Incomplete expression: " + this)
                lp.accept(visitor, params)
            }
        }
}
