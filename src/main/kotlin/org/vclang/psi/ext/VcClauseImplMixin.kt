package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcClause


abstract class VcClauseImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcClause {
    override fun getData(): Any? = this

    override fun getPatterns(): List<Abstract.Pattern> = patternList

    override fun getExpression(): Abstract.Expression? = expr
}