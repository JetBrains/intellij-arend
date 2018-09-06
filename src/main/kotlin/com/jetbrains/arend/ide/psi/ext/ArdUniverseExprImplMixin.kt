package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdUniverseExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdUniverseExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdUniverseExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        appExpr?.let { return it.accept(visitor, params) }
        propKw?.let { return visitor.visitUniverse(it, 0, -1, null, null, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
        error("Incorrect expression: universeExpr")
    }
}