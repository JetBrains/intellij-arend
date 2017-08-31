package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcNewExpr
import org.vclang.lang.core.resolve.EmptyScope
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcNewExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcNewExpr {
    override val namespace: Namespace
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope ?: EmptyScope
            return NamespaceProvider.forExpression(this, parentScope)
        }
}
