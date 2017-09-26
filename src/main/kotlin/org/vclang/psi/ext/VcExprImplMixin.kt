package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcExpr


abstract class VcExprImplMixin(node: ASTNode): VcCompositeElementImpl(node), VcExpr {
    override fun getData(): VcExprImplMixin = this

    override fun isExplicit(): Boolean = true

    override fun getReferableList(): List<Referable?> = listOf(null)

    override fun getType(): Abstract.Expression = this
}