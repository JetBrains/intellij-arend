package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcOnlyLevelExpr


abstract class VcOnlyLevelExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcOnlyLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        val result = when {
            sucKw != null -> atomLevelExprList.firstOrNull().let { visitor.visitSuc(this, it, params) }
            maxKw != null -> {
                val levelExprs = atomLevelExprList
                levelExprs.getOrNull(1).let { visitor.visitMax(this, levelExprs[0], it, params) }
            }
            else -> atomOnlyLevelExpr?.accept(visitor, params)
        }
        return result ?: error("Incomplete expression: " + this)
    }
}
