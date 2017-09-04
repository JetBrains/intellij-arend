package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcNewExpr
import org.vclang.resolve.EmptyScope
import org.vclang.resolve.Namespace
import org.vclang.resolve.NamespaceProvider

abstract class VcNewExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcNewExpr {
    override val namespace: Namespace
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope ?: EmptyScope
            return NamespaceProvider.forExpression(this, parentScope)
        }
}
