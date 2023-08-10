package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import org.arend.naming.reference.ClassReferable
import org.arend.psi.*
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.term.abs.Abstract


class ArendLetClause(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.LetClause {
    override fun getReferable(): ArendDefIdentifier? = childOfType()

    override fun getPattern(): ArendPattern? = childOfType()

    override fun getParameters(): List<ArendNameTele> = getChildrenOfType()

    override fun getResultType(): ArendExpr? = childOfType(ArendElementTypes.COLON)?.findNextSibling() as? ArendExpr

    override fun getTerm(): ArendExpr? = childOfType(ArendElementTypes.FAT_ARROW)?.findNextSibling() as? ArendExpr

    override fun getUseScope() = LocalSearchScope(parent)

    val typeClassReference: ClassReferable?
        get() {
            val type = resultType ?: (term as? ArendNewExpr)?.let { if (it.appPrefix?.isNew == true) it.argumentAppExpr else null } ?: return null
            return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
        }
}