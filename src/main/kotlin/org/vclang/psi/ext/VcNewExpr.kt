package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcNewExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcNewExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcNewExpr {
    /* TODO[abstract]
    override val namespace: Namespace
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope ?: EmptyScope
            return NamespaceProvider.forExpression(this, parentScope)
        }
    */
}
