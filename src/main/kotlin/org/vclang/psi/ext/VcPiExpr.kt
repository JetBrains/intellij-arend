package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcPiExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcPiExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcPiExpr {
    /* TODO[abstract]
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
    */
}
