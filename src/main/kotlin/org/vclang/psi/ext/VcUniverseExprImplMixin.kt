package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcUniverseExpr


abstract class VcUniverseExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcUniverseExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        appExpr?.let { return it.accept(visitor, params) }
        propKw?.let { return visitor.visitUniverse(it, 0, -1, null, null, params) }
        error("Incorrect expression: universeExpr")
    }
}