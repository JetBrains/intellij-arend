package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.psi.ArendClause


abstract class ArendClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendClause {
    override fun getData(): Any? = this

    override fun getPatterns(): List<Abstract.Pattern> = patternList

    override fun getExpression(): Abstract.Expression? = expr
}