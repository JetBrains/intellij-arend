package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcArgument
import org.vclang.psi.VcExpr


abstract class VcArgumentImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcArgument {
    override fun isExplicit(): Boolean = lbrace == null

    override fun getExpression(): VcExpr = atomFieldsAcc ?: expr ?: universeAtom ?: error("Incorrect expression")
}