package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendAppHole
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendAppHoleImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendAppHole {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
            visitor.visitApplyHole(this, params)
}