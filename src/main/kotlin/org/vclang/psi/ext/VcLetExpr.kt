package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcLetExpr

abstract class VcLetExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcLetExpr {
//    TODO[abstract]
//    override val namespace: Namespace
//        get() = NamespaceProvider.forExpression(this)
}
