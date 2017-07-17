package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcSigmaExpr
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class VcSigmaExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                     VcSigmaExpr {
    override val namespace: Namespace
        get() = NamespaceProvider.forExpression(this)
}
