package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcAtomLevelExprLP


abstract class VcAtomLevelExprLPImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomLevelExprLP {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        number?.text?.toIntOrNull()?.let { return visitor.visitNumber(this, it, params) }
        levelExprLP?.let { return it.accept(visitor, params) }
        error("Incomplete expression: " + this)
    }
}