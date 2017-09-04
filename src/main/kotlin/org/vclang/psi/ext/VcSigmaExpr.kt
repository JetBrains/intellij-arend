package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcSigmaExpr
import org.vclang.resolve.Namespace
import org.vclang.resolve.NamespaceProvider

abstract class VcSigmaExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcSigmaExpr {
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
}
