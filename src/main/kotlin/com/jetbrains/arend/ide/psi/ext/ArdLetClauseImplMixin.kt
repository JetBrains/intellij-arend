package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.jetbrains.arend.ide.psi.ArdExpr
import com.jetbrains.arend.ide.psi.ArdLetClause
import com.jetbrains.arend.ide.psi.ArdLetExpr
import com.jetbrains.arend.ide.psi.ArdNameTele
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract


abstract class ArdLetClauseImplMixin(node: ASTNode) : PsiReferableImpl(node), ArdLetClause, ArdSourceNode {
    override fun getReferable(): Referable = this

    override fun getParameters(): List<ArdNameTele> = nameTeleList

    override fun getResultType(): ArdExpr? = typeAnnotation?.expr

    override fun getTerm(): ArdExpr? = expr

    override fun getUseScope(): SearchScope {
        if (parent is ArdLetExpr) return LocalSearchScope(parent)
        return super.getUseScope()
    }

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getTopmostEquivalentSourceNode() = com.jetbrains.arend.ide.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = com.jetbrains.arend.ide.psi.ext.getParentSourceNode(this)

    override fun getErrorData(): Abstract.ErrorData? = com.jetbrains.arend.ide.psi.ext.getErrorData(this)

    override val psiElementType: PsiElement?
        get() = resultType
}