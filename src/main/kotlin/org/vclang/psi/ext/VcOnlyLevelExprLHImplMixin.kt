package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.*


abstract class VcOnlyLevelExprLHImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcOnlyLevelExprLH {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R =
        when {
            sucKw != null -> visitor.visitSuc(this, atomLevelExprLHList.firstOrNull(), params)
            maxKw != null -> {
                val levelExprs = atomLevelExprLHList
                visitor.visitMax(this, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
            }
            else -> {
                val lp = atomOnlyLevelExprLH ?: error("Incomplete expression: " + this)
                lp.accept(visitor, params)
            }
        }
}
