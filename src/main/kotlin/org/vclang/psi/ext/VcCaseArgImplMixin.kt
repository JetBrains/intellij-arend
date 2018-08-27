package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcCaseArg
import org.vclang.psi.VcExpr


abstract class VcCaseArgImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcCaseArg {
    override fun getExpression(): VcExpr = exprList[0]

    override fun getReferable() = defIdentifier

    override fun getType() = exprList.getOrNull(1)
}