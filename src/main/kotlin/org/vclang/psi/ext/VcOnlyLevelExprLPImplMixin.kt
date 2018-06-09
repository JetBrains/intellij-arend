package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.*


abstract class VcOnlyLevelExprLPImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcOnlyLevelExprLP {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R =
        when {
            sucKw != null -> visitor.visitSuc(this, atomLevelExprLPList.firstOrNull(), params)
            maxKw != null -> {
                val levelExprs = atomLevelExprLPList
                visitor.visitMax(this, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
            }
            else -> {
                val lp = atomOnlyLevelExprLP ?: error("Incomplete expression: " + this)
                lp.accept(visitor, params)
            }
        }
}
