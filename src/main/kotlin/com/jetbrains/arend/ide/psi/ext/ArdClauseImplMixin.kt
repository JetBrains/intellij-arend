package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdClause
import com.jetbrains.jetpad.vclang.term.abs.Abstract


abstract class ArdClauseImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdClause {
    override fun getData(): Any? = this

    override fun getPatterns(): List<Abstract.Pattern> = patternList

    override fun getExpression(): Abstract.Expression? = expr
}