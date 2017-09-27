package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcArgument


abstract class VcArgumentImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcArgument {
    override fun isExplicit(): Boolean = lbrace == null

    override fun getExpression(): Abstract.Expression = expr
}