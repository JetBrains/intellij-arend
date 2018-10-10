package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.typing.ReferableExtractVisitor

abstract class ArendNewExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ClassReferenceHolder {
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

    override fun getClassReference(): ClassReferable? {
        val argAppExpr = getAppExpr() as? ArendArgumentAppExpr ?: getArgumentAppExpr() ?: return null
        val ref = argAppExpr.accept(ReferableExtractVisitor(), null)
        return (if (ref is UnresolvedReference) ExpressionResolveNameVisitor.resolve(ref, argAppExpr.scope.globalSubscope) else ref) as? ClassReferable
    }

    override fun getClassReferenceData(): ClassReferenceData? {
        val argAppExpr = getAppExpr() as? ArendArgumentAppExpr ?: getArgumentAppExpr() ?: return null
        val visitor = ReferableExtractVisitor(true)
        val ref = argAppExpr.accept(visitor, null)
        val classRef = (if (ref is UnresolvedReference) ExpressionResolveNameVisitor.resolve(ref, argAppExpr.scope.globalSubscope) else ref) as? ClassReferable ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, emptyList())
    }

    override fun getClassFieldImpls(): List<ArendCoClause> = getCoClauseList()
}
