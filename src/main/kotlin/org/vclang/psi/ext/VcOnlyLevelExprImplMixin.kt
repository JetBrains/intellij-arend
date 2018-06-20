package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.*


abstract class VcOnlyLevelExprImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcOnlyLevelExpr {
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
