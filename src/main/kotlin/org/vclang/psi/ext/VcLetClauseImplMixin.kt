package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.VcExpr
import org.vclang.psi.VcLetClause
import org.vclang.psi.VcTele


abstract class VcLetClauseImplMixin(node: ASTNode) : PsiReferableImpl(node), VcLetClause {
    override fun getReferable(): Referable = this

    override fun getParameters(): List<VcTele> = teleList

    override fun getResultType(): VcExpr? = typeAnnotation?.expr

    override fun getTerm(): VcExpr? = expr
}