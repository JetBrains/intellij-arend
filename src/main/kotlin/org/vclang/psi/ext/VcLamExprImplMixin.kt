package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcLamExpr

abstract class VcLamExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcLamExpr {
    override fun <P : Any, R : Any> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R =
        visitor.visitLam(this, teleList, expr, params)

/* TODO[abstract]
    override val scope: Scope
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope
            val namespaceScope = NamespaceScope(NamespaceProvider.forExpression(this))
            parentScope?.let { return OverridingScope(it, namespaceScope) }
            return namespaceScope
        }
    */
}
