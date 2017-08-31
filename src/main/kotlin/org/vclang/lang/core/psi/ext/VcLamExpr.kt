package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcLamExpr
import org.vclang.lang.core.resolve.NamespaceProvider
import org.vclang.lang.core.resolve.NamespaceScope
import org.vclang.lang.core.resolve.OverridingScope
import org.vclang.lang.core.resolve.Scope

abstract class VcLamExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcLamExpr {
    override val scope: Scope
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope
            val namespaceScope = NamespaceScope(NamespaceProvider.forExpression(this))
            parentScope?.let { return OverridingScope(it, namespaceScope) }
            return namespaceScope
        }
}
