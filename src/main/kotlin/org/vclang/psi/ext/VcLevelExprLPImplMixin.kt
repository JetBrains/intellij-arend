package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import com.intellij.lang.ASTNode
import org.vclang.psi.VcLevelExprLP


abstract class VcLevelExprLPImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcLevelExprLP {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        val levelExprs = atomLevelExprLPList
        return when {
            sucKw != null -> visitor.visitSuc(this, levelExprs.firstOrNull(), params)
            maxKw != null -> visitor.visitMax(this, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
            else -> levelExprs[0].accept(visitor, params)
        }
    }
}
