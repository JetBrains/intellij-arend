package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.reference.ClassReferable
import org.arend.psi.*
import org.arend.resolving.util.ReferableExtractVisitor


abstract class ArendLetClauseImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendLetClause {
    override fun getReferable() = defIdentifier

    override fun getPattern() = PsiTreeUtil.getChildOfType(this, org.arend.psi.parser.api.ArendPattern::class.java)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = typeAnnotation?.expr

    override fun getTerm(): ArendExpr? = expr

    override fun getUseScope() = LocalSearchScope(parent)

    val typeClassReference: ClassReferable?
        get() {
            val type = resultType ?: (expr as? ArendNewExpr)?.let { if (it.appPrefix?.newKw != null) it.argumentAppExpr else null } ?: return null
            return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
        }

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}