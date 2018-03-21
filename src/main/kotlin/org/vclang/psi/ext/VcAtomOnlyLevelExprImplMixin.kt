package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcAtomOnlyLevelExpr


abstract class VcAtomOnlyLevelExprImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomOnlyLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        lhKw?.let { return visitor.visitLH(this, params) }
        return onlyLevelExpr?.accept(visitor, params) ?: error("Incomplete expression: " + this)
    }
}
