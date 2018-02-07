package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.VcExpr
import org.vclang.psi.VcLetClause
import org.vclang.psi.VcLetExpr
import org.vclang.psi.VcNameTele


abstract class VcLetClauseImplMixin(node: ASTNode) : PsiReferableImpl(node), VcLetClause {
    override fun getReferable(): Referable = this

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultType(): VcExpr? = typeAnnotation?.expr

    override fun getTerm(): VcExpr? = expr

    override fun getUseScope(): SearchScope {
        if (parent is VcLetExpr) return LocalSearchScope(parent)
        return super.getUseScope()
    }
}