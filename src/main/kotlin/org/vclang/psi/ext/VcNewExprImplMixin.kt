package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAppExpr
import org.vclang.psi.VcArgument
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcCoClause

abstract class VcNewExprImplMixin(node: ASTNode) : VcExprImplMixin(node), Abstract.ClassReferenceHolder {
    abstract fun getNewKw(): PsiElement?

    abstract fun getLbrace(): PsiElement?

    abstract fun getArgumentAppExpr(): VcArgumentAppExpr?

    open fun getAppExpr(): VcAppExpr? = null

    open fun getArgumentList(): List<VcArgument> = emptyList()

    abstract fun getCoClauseList(): List<VcCoClause>

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val isNew = getNewKw() != null
        if (!isNew && getLbrace() == null && getArgumentList().isEmpty()) {
            val expr = getAppExpr() ?: return visitor.visitInferHole(this, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
            return expr.accept(visitor, params)
        }
        return visitor.visitClassExt(this, isNew, if (isNew) getArgumentAppExpr() else getAppExpr(), if (getLbrace() == null) null else getCoClauseList(), getArgumentList(), if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
    }

    override fun getClassReference(): ClassReferable? = getClassReference(getAppExpr()) ?: getClassReference(getArgumentAppExpr())

    override fun getClassFieldImpls(): List<VcCoClause> = getCoClauseList()

    override fun getNumberOfArguments() = getArgumentAppExpr()?.argumentList?.size ?: 0

    companion object {
        fun getClassReference(appExpr: VcAppExpr?): ClassReferable? {
            val argAppExpr = appExpr as? VcArgumentAppExpr ?: return null
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
