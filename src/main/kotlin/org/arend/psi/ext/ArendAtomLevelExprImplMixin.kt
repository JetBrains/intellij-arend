package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractLevelExpressionVisitor
import org.arend.psi.ArendAtomLevelExpr
import org.arend.term.abs.AbstractExpressionError


abstract class ArendAtomLevelExprImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        lhKw?.let { return visitor.visitLH(this, params) }
        number?.text?.toIntOrNull()?.let { return visitor.visitNumber(this, it, params) }
        levelExpr?.let { return it.accept(visitor, params) }
        throw AbstractExpressionError.Exception(AbstractExpressionError.incomplete(this))
    }
}