package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.naming.reference.TypedReferable
import org.arend.naming.scope.LazyScope
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.resolving.util.ReferableExtractVisitor

class ArendNewExpr(node: ASTNode) : ArendExpr(node), ClassReferenceHolder, ArendArgument {
    val appPrefix: ArendAppPrefix?
        get() = childOfType()

    val lbrace: PsiElement?
        get() = findChildByType(ArendElementTypes.LBRACE)

    val rbrace: PsiElement?
        get() = findChildByType(ArendElementTypes.RBRACE)

    val argumentAppExpr: ArendArgumentAppExpr?
        get() = childOfType()

    val appExpr: ArendAppExpr?
        get() = childOfType()

    val argumentList: List<ArendArgument>
        get() = getChildrenOfType()

    val localCoClauseList: List<ArendLocalCoClause>
        get() = getChildrenOfType()

    val withBody: ArendWithBody?
        get() = childOfType()

    override fun isVariable() = false

    override fun getExpression() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val prefix = appPrefix
        val body = withBody
        val lbrace = lbrace
        if (prefix == null && lbrace == null && argumentList.isEmpty() && body == null) {
            val expr = appExpr ?: return visitor.visitInferHole(this, params)
            return expr.accept(visitor, params)
        }
        val evalKind = prefix?.evalKind
        return visitor.visitClassExt(this, prefix?.isNew == true, evalKind, if (prefix != null) argumentAppExpr else appExpr, lbrace, if (lbrace == null) null else localCoClauseList, argumentList, body, params)
    }

    private fun getClassReference(onlyClassRef: Boolean, withAdditionalInfo: Boolean): ClassReferenceData? {
        val evalKind = appPrefix?.evalKind
        if (evalKind != null && evalKind != Abstract.EvalKind.EVAL) {
            return null
        }

        val visitor = ReferableExtractVisitor(withAdditionalInfo)
        val expr = appExpr as? ArendArgumentAppExpr ?: argumentAppExpr
        val ref = visitor.findReferable(expr)
        val classRef = (if (expr != null) visitor.findClassReference(ref, LazyScope { expr.scope }) else null) ?: (if (!onlyClassRef && appPrefix?.isNew == true && ref is TypedReferable) ref.typeClassReference else null) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, emptySet(), false)
    }

    override fun getClassReference() = getClassReference(onlyClassRef = false, withAdditionalInfo = false)?.classRef

    override fun getClassReferenceData(onlyClassRef: Boolean) = getClassReference(onlyClassRef, true)

    override fun getCoClauseElements(): List<ArendLocalCoClause> = if (appPrefix?.evalKind.let { it  == Abstract.EvalKind.EVAL || it == null }) localCoClauseList else emptyList()
}
