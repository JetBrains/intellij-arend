package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLetClausePattern
import org.arend.term.abs.Abstract


abstract class ArendLetClausePatternImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendLetClausePattern {
    override fun getReferable() = defIdentifier

    override fun getType(): ArendExpr? = typeAnnotation?.expr

    override fun getPatterns(): List<ArendLetClausePattern> = letClausePatternList

    override fun getTopmostEquivalentSourceNode() = org.arend.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = org.arend.psi.ext.getParentSourceNode(this)

    override fun getErrorData(): Abstract.ErrorData? = org.arend.psi.ext.getErrorData(this)
}