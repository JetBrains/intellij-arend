package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcMaxLevelExpr


abstract class VcMaxLevelExprImplMixin(node: ASTNode) : VcLevelExprImplMixin(node), VcMaxLevelExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        val levelExprs = atomLevelExprList
        return visitor.visitMax(this, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
    }
}