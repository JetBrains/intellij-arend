package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcExpr
import org.vclang.psi.VcLetClause
import org.vclang.psi.VcLetExpr
import org.vclang.psi.VcNameTele
import org.vclang.typing.ExpectedTypeVisitor


abstract class VcLetClauseImplMixin(node: ASTNode) : PsiReferableImpl(node), VcLetClause, VcSourceNode {
    override fun getReferable(): Referable = this

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultType(): VcExpr? = typeAnnotation?.expr

    override fun getTerm(): VcExpr? = expr

    override fun getUseScope(): SearchScope {
        if (parent is VcLetExpr) return LocalSearchScope(parent)
        return super.getUseScope()
    }

    override fun getParameterType(index: Int) = ExpectedTypeVisitor.getParameterType(parameters, resultType, index)

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getTopmostEquivalentSourceNode() = org.vclang.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = org.vclang.psi.ext.getParentSourceNode(this)

    override fun getErrorData(): Abstract.ErrorData? = org.vclang.psi.ext.getErrorData(this)

    override val psiElementType: PsiElement?
        get() = resultType
}