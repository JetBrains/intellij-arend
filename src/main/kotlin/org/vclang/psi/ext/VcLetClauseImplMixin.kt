package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcLetClause


abstract class VcLetClauseImplMixin(node: ASTNode) : PsiReferableImpl(node), VcLetClause {
    override fun getReferable(): Referable = this

    override fun getParameters(): List<Abstract.Parameter> = teleList

    override fun getResultType(): Abstract.Expression? = typeAnnotation?.expr

    override fun getTerm(): Abstract.Expression = expr // TODO[abstract]: what if the expression is missing?
}