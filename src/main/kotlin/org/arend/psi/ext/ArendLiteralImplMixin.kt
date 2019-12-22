package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLiteral


abstract class ArendLiteralImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLiteral {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        (ipName as? ArendIPNameImplMixin)?.let {
            return visitor.visitReference(it, it.referent, it.fixity, null, null, params)
        }
        longName?.let {
            return visitor.visitReference(it, it.referent, null, null, null, params)
        }
        if (propKw != null) {
            return visitor.visitUniverse(this, 0, -1, null, null, params)
        }
        if (underscore != null) {
            return visitor.visitInferHole(this, params)
        }
        goal?.let {
            return visitor.visitGoal(it, it.defIdentifier?.textRepresentation(), it.expr, params)
        }
        error("Incorrect expression: literal")
    }
}