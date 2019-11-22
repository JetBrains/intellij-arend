package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendCaseArg


abstract class ArendCaseArgImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendCaseArg {
    override fun getExpression() = caseArgExprAs.expr

    override fun getReferable() = caseArgExprAs.defIdentifier

    override fun getType() = expr

    override fun getEliminatedReference() = caseArgExprAs.refIdentifier
}