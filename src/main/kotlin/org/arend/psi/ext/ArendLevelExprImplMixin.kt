package org.arend.psi.ext

import org.arend.term.abs.AbstractLevelExpressionVisitor
import com.intellij.lang.ASTNode
import org.arend.psi.ArendLevelExpr


abstract class ArendLevelExprImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        val levelExprs = atomLevelExprList
        return when {
            sucKw != null -> visitor.visitSuc(this, levelExprs.firstOrNull(), params)
            maxKw != null -> visitor.visitMax(this, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
            else -> levelExprs[0].accept(visitor, params)
        }
    }
}
