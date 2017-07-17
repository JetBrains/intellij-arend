package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcLamExpr
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcLamExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcLamExpr {
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
}
