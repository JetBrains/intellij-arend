package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdAtomLevelExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractLevelExpressionVisitor


abstract class ArdAtomLevelExprImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdAtomLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        lhKw?.let { return visitor.visitLH(this, params) }
        number?.text?.toIntOrNull()?.let { return visitor.visitNumber(this, it, params) }
        levelExpr?.let { return it.accept(visitor, params) }
        error("Incomplete expression: " + this)
    }
}