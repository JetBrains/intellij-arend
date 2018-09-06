package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.psi.ArdAppExpr
import com.jetbrains.arend.ide.psi.ArdArgument
import com.jetbrains.arend.ide.psi.ArdArgumentAppExpr
import com.jetbrains.arend.ide.psi.ArdCoClause
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor

abstract class ArdNewExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), Abstract.ClassReferenceHolder {
    abstract fun getNewKw(): PsiElement?

    abstract fun getLbrace(): PsiElement?

    abstract fun getArgumentAppExpr(): ArdArgumentAppExpr?

    open fun getAppExpr(): ArdAppExpr? = null

    open fun getArgumentList(): List<ArdArgument> = emptyList()

    abstract fun getCoClauseList(): List<ArdCoClause>

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val isNew = getNewKw() != null
        if (!isNew && getLbrace() == null && getArgumentList().isEmpty()) {
            val expr = getAppExpr()
                    ?: return visitor.visitInferHole(this, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
            return expr.accept(visitor, params)
        }
        return visitor.visitClassExt(this, isNew, if (isNew) getArgumentAppExpr() else getAppExpr(), if (getLbrace() == null) null else getCoClauseList(), getArgumentList(), if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
    }

    override fun getClassReference(): ClassReferable? = getClassReference(getAppExpr())
            ?: getClassReference(getArgumentAppExpr())

    override fun getClassFieldImpls(): List<ArdCoClause> = getCoClauseList()

    override fun getArgumentsExplicitness() = getArgumentAppExpr()?.argumentList?.map { it.isExplicit } ?: emptyList()

    companion object {
        fun getClassReference(appExpr: ArdAppExpr?): ClassReferable? {
            val argAppExpr = appExpr as? ArdArgumentAppExpr ?: return null
            var ref = argAppExpr.longNameExpr?.longName?.referent
            if (ref == null) {
                val atomFieldsAcc = argAppExpr.atomFieldsAcc ?: return null
                if (!atomFieldsAcc.fieldAccList.isEmpty()) {
                    return null
                }
                ref = atomFieldsAcc.atom.literal?.longName?.referent
            }
            return ExpressionResolveNameVisitor.resolve(ref, appExpr.scope.globalSubscope) as? ClassReferable
        }
    }
}
