package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.TypedReferable
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
        val onlyClassRef = getNewKw() == null
        val resolvedRef = if (ref is UnresolvedReference) ExpressionResolveNameVisitor.resolve(ref, if (onlyClassRef) argAppExpr.scope.globalSubscope else argAppExpr.scope) else ref
        return resolvedRef as? ClassReferable ?: if (!onlyClassRef && resolvedRef is TypedReferable) resolvedRef.typeClassReference else null
    }

    override fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData? {
        val argAppExpr = getAppExpr() as? ArendArgumentAppExpr ?: getArgumentAppExpr() ?: return null
        val visitor = ReferableExtractVisitor(true)
        val ref = argAppExpr.accept(visitor, null)
        val realOnlyClassRef = onlyClassRef || getNewKw() == null
        val resolvedRef = if (ref is UnresolvedReference) ExpressionResolveNameVisitor.resolve(ref, if (realOnlyClassRef) argAppExpr.scope.globalSubscope else argAppExpr.scope) else ref
        val classRef = resolvedRef as? ClassReferable ?: (if (!realOnlyClassRef && resolvedRef is TypedReferable) resolvedRef.typeClassReference else null) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, emptyList())
    }

    override fun getClassFieldImpls(): List<ArendCoClause> = getCoClauseList()
}
