package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.*


abstract class VcAtomOnlyLevelExprLPImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomOnlyLevelExprLP {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        onlyLevelExprLP?.let { return it.accept(visitor, params) }
        error("Incomplete expression: " + this)
    }
}
