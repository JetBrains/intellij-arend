package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendClause
import org.arend.term.abs.Abstract


abstract class ArendClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendClause {
    override fun getData(): Any? = this

    override fun getPatterns(): List<Abstract.Pattern> = patternList

    override fun getExpression(): Abstract.Expression? = expr
}