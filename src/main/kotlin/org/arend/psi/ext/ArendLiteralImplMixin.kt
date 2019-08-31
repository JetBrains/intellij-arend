package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendExpr
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLiteral


abstract class ArendLiteralImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLiteral {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        (argument as? ArendExpr)?.let {
            return it.accept(visitor, params)
        }
        longName?.let {
            return visitor.visitReference(it, it.referent, null, null, null, if (visitor.visitErrors()) getErrorData(this) else null, params)
        }
        if (propKw != null) {
            return visitor.visitUniverse(this, 0, -1, null, null, if (visitor.visitErrors()) getErrorData(this) else null, params)
        }
        if (underscore != null) {
            return visitor.visitInferHole(this, if (visitor.visitErrors()) getErrorData(this) else null, params)
        }
        goal?.let {
            return visitor.visitGoal(it, it.defIdentifier?.textRepresentation(), it.expr, if (visitor.visitErrors()) getErrorData(this) else null, params)
        }
        error("Incorrect expression: literal")
    }
}