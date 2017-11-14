package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcLevelExpr


abstract class VcLevelExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcLevelExpr {
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