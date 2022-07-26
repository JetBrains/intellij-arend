package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.arend.term.abs.Abstract
import org.arend.psi.ArendClause
import org.arend.psi.parser.api.ArendPattern


abstract class ArendClauseImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendClause {
    override fun getData(): Any? = this

    override fun getPatterns(): List<Abstract.Pattern> = PsiTreeUtil.getChildrenOfTypeAsList(this, ArendPattern::class.java)

    override fun getExpression(): Abstract.Expression? = expr
}