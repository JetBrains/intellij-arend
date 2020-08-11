package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import org.arend.naming.reference.ClassReferable
import org.arend.psi.ArendAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLetClause
import org.arend.psi.ArendNameTele
import org.arend.term.abs.Abstract
import org.arend.typing.ReferableExtractVisitor


abstract class ArendLetClauseImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendLetClause {
    override fun getReferable() = defIdentifier

    override fun getPattern(): Abstract.LetClausePattern? = letClausePattern

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = typeAnnotation?.expr

    override fun getTerm(): ArendExpr? = expr

    override fun getUseScope() = LocalSearchScope(parent)

    val typeClassReference: ClassReferable?
        get() {
            val type = resultType ?: (expr as? ArendAppExpr)?.let { if (it.appKeyword?.newKw != null) it.argumentAppExpr else null } ?: return null
            return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
        }

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}