package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcSigmaExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcSigmaExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcSigmaExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R =
        visitor.visitSigma(this, teleList, params)

    /* TODO[abstract]
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
    */
}
