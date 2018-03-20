package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcAtomLevelExpr


abstract class VcAtomLevelExprImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        lhKw?.let { return visitor.visitLH(this, params) }
        number?.text?.toIntOrNull()?.let { return visitor.visitNumber(this, it, params) }
        return levelExpr?.accept(visitor, params) ?: error("Incomplete expression: " + this)
    }
}