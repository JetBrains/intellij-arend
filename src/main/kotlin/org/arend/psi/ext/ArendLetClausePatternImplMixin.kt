package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLetClausePattern


abstract class ArendLetClausePatternImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendLetClausePattern {
    override fun isIgnored() = underscore != null

    override fun getReferable() = defIdentifier

    override fun getType(): ArendExpr? = typeAnnotation?.expr

    override fun getPatterns(): List<ArendLetClausePattern> = letClausePatternList

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)

    override fun getErrorData() = getErrorData(this)
}