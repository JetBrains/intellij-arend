package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLamExpr
import org.arend.psi.ArendLamParam
import org.arend.psi.ArendLamTele
import org.arend.resolving.ArendReference

abstract class ArendLamParameterImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendLamParam, ArendCompositeElement {
    override fun getData(): Any? {
        return this
    }
}
