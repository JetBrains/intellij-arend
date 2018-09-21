package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendCaseArg
import org.arend.psi.ArendExpr


abstract class ArendCaseArgImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendCaseArg {
    override fun getExpression(): ArendExpr = exprList[0]

    override fun getReferable() = defIdentifier

    override fun getType() = exprList.getOrNull(1)
}