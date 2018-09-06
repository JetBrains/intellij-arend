package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdAtomOnlyLevelExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor


abstract class ArdAtomOnlyLevelExprImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdAtomOnlyLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        lhKw?.let { return visitor.visitLH(this, params) }
        onlyLevelExpr?.let { return it.accept(visitor, params) }
        error("Incomplete expression: " + this)
    }
}
