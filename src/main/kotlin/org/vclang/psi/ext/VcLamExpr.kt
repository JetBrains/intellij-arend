package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcLamExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcLamExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcLamExpr {
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
