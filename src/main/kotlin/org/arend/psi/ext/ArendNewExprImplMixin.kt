package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendAppExpr
import org.arend.psi.ArendArgument
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendCoClause

abstract class ArendNewExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), Abstract.ClassReferenceHolder {
    abstract fun getNewKw(): PsiElement?

    abstract fun getLbrace(): PsiElement?

    abstract fun getArgumentAppExpr(): ArendArgumentAppExpr?

    open fun getAppExpr(): ArendAppExpr? = null

    open fun getArgumentList(): List<ArendArgument> = emptyList()

    abstract fun getCoClauseList(): List<ArendCoClause>

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val isNew = getNewKw() != null
        if (!isNew && getLbrace() == null && getArgumentList().isEmpty()) {
            val expr = getAppExpr() ?: return visitor.visitInferHole(this, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
            return expr.accept(visitor, params)
        }
        return visitor.visitClassExt(this, isNew, if (isNew) getArgumentAppExpr() else getAppExpr(), if (getLbrace() == null) null else getCoClauseList(), getArgumentList(), if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
    }

    override fun getClassReference(): ClassReferable? = getClassReference(getAppExpr()) ?: getClassReference(getArgumentAppExpr())

    override fun getClassFieldImpls(): List<ArendCoClause> = getCoClauseList()

    override fun getArgumentsExplicitness() = getArgumentAppExpr()?.argumentList?.map { it.isExplicit } ?: emptyList()

    companion object {
        fun getClassReference(appExpr: ArendAppExpr?): ClassReferable? {
            val argAppExpr = appExpr as? ArendArgumentAppExpr ?: return null
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
