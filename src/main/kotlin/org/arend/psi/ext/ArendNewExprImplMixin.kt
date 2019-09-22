package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.*
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.typing.ReferableExtractVisitor

abstract class ArendNewExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ClassReferenceHolder {
    abstract val newKw: PsiElement?

    abstract val lbrace: PsiElement?

    abstract val argumentAppExpr: ArendArgumentAppExpr?

    open val appExpr: ArendAppExpr?
        get() = null

    open val argumentList: List<ArendArgument>
        get() = emptyList()

    abstract val coClauseList: List<ArendCoClause>

    fun isVariable() = false

    fun getExpression() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val isNew = newKw != null
        if (!isNew && lbrace == null && argumentList.isEmpty()) {
            val expr = appExpr ?: return visitor.visitInferHole(this, if (visitor.visitErrors()) getErrorData(this) else null, params)
            return expr.accept(visitor, params)
        }
        return visitor.visitClassExt(this, isNew, if (isNew) argumentAppExpr else appExpr, if (lbrace == null) null else coClauseList, argumentList, if (visitor.visitErrors()) getErrorData(this) else null, params)
    }

    private fun getClassReference(onlyClassRef: Boolean, withAdditionalInfo: Boolean): ClassReferenceData? {
        val visitor = ReferableExtractVisitor(withAdditionalInfo)
        val ref = visitor.findReferable(appExpr as? ArendArgumentAppExpr ?: argumentAppExpr)
        val classRef = ref as? ClassReferable ?: (if (!onlyClassRef && newKw != null && ref is TypedReferable) ref.typeClassReference else null) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, emptySet(), false)
    }

    override fun getClassReference() = getClassReference(onlyClassRef = false, withAdditionalInfo = false)?.classRef

    override fun getClassReferenceData(onlyClassRef: Boolean) = getClassReference(onlyClassRef, true)

    override fun getClassFieldImpls(): List<ArendCoClause> = coClauseList
}
