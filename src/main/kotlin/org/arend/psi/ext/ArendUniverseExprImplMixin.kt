package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendUniverseExpr
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendUniverseExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendUniverseExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        appExpr?.let { return it.accept(visitor, params) }
        propKw?.let { return visitor.visitUniverse(this, 0, -1, null, null, if (visitor.visitErrors()) getErrorData(this) else null, params) }
        error("Incorrect expression: universeExpr")
    }
}