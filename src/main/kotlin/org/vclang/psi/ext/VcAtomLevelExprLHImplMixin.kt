package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.VcAtomLevelExprLH


abstract class VcAtomLevelExprLHImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomLevelExprLH {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lhKw?.let { return visitor.visitLP(this, params) }
        number?.text?.toIntOrNull()?.let { return visitor.visitNumber(this, it, params) }
        levelExprLH?.let { return it.accept(visitor, params) }
        error("Incomplete expression: " + this)
    }
}
