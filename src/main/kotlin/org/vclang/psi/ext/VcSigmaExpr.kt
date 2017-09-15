package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcSigmaExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcSigmaExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcSigmaExpr {
    /* TODO[abstract]
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
    */
}
