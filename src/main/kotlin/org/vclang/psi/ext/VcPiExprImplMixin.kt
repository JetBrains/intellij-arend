package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcPiExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcPiExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcPiExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitPi(this, teleList, expr, params)

    /* TODO[abstract]
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
    */
}
