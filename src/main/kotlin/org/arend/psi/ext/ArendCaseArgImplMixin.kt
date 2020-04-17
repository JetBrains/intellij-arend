package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendCaseArg
import org.arend.psi.ArendExpr
import org.arend.psi.impl.ArendLiteralImpl


abstract class ArendCaseArgImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendCaseArg {
    override fun getExpression() = caseArgExprAs.expr
            ?: caseArgExprAs.applyHole?.node?.let(::ArendLiteralImpl)

    override fun getReferable() = caseArgExprAs.defIdentifier

    override fun getType() = expr

    override fun getEliminatedReference() = caseArgExprAs.refIdentifier
}