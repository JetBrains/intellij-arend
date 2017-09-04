package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcPiExpr
import org.vclang.resolve.Namespace
import org.vclang.resolve.NamespaceProvider

abstract class VcPiExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcPiExpr {
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
}
