package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.util.castSafelyTo
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendArgument
import org.arend.psi.ArendAtomArgument
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLamExpr
import org.arend.psi.ArendLamParam
import org.arend.psi.ArendLamTele
import org.arend.resolving.ArendReference

abstract class ArendArgumentImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendArgument, ArendCompositeElement {

    override fun isExplicit(): Boolean {
        return true
    }

    override fun getExpression(): Abstract.Expression? {
        return this.castSafelyTo<ArendAtomArgument>()?.expression
    }

    override fun isVariable(): Boolean {
        return this.castSafelyTo<ArendAtomArgument>()?.isVariable ?: false
    }
}
