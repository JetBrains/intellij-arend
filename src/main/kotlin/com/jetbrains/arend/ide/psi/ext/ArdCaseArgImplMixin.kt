package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdCaseArg
import com.jetbrains.arend.ide.psi.ArdExpr


abstract class ArdCaseArgImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdCaseArg {
    override fun getExpression(): ArdExpr = exprList[0]

    override fun getReferable() = defIdentifier

    override fun getType() = exprList.getOrNull(1)
}