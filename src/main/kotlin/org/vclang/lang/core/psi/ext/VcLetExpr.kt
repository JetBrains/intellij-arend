package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.lang.core.psi.VcLetExpr

abstract class VcLetExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcLetExpr {
//    override val namespace: Namespace
//        get() = NamespaceProvider.forExpression(this)
}
