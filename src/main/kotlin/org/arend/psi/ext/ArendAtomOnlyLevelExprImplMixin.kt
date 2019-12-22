package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendAtomOnlyLevelExpr
import org.arend.term.abs.AbstractLevelExpressionVisitor


abstract class ArendAtomOnlyLevelExprImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendAtomOnlyLevelExpr {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        lpKw?.let { return visitor.visitLP(this, params) }
        lhKw?.let { return visitor.visitLH(this, params) }
        ooKw?.let { return visitor.visitInf(this, params) }
        onlyLevelExpr?.let { return it.accept(visitor, params) }
        return visitor.visitError(this)
    }
}
