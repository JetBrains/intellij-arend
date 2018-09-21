package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.arend.naming.reference.Referable
import org.arend.term.abs.Abstract
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLetClause
import org.arend.psi.ArendLetExpr
import org.arend.psi.ArendNameTele
import org.arend.typing.ExpectedTypeVisitor


abstract class ArendLetClauseImplMixin(node: ASTNode) : PsiReferableImpl(node), ArendLetClause, ArendSourceNode {
    override fun getReferable(): Referable = this

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = typeAnnotation?.expr

    override fun getTerm(): ArendExpr? = expr

    override fun getUseScope(): SearchScope {
        if (parent is ArendLetExpr) return LocalSearchScope(parent)
        return super.getUseScope()
    }

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getTopmostEquivalentSourceNode() = org.arend.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = org.arend.psi.ext.getParentSourceNode(this)

    override fun getErrorData(): Abstract.ErrorData? = org.arend.psi.ext.getErrorData(this)

    override val psiElementType: PsiElement?
        get() = resultType
}