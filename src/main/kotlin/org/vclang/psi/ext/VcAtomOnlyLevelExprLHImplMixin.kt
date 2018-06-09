package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor
import org.vclang.psi.*


abstract class VcAtomOnlyLevelExprLHImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcAtomOnlyLevelExprLH {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lhKw?.let { return visitor.visitLH(this, params) }
        onlyLevelExprLH?.let { return it.accept(visitor, params) }
        error("Incomplete expression: " + this)
    }
}

