package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcPiExpr
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcPiExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcPiExpr {
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
}
