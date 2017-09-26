package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcLevelExpr


abstract class VcLevelExprImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcLevelExpr {
    override fun getData(): VcLevelExprImplMixin = this
}