package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcNewExpr
import org.vclang.lang.core.resolve.EmptyNamespace
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcNewExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcNewExpr {
    override val namespace: Namespace
        get() {
            val parent = parent as? VcCompositeElement
            return parent?.let { NamespaceProvider.forExpression(this, it.scope) } ?: EmptyNamespace
        }
}
