package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcLiteral


abstract class VcLiteralImplMixin(node: ASTNode) : VcExprImplMixin(node), VcLiteral {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        longName?.let { return visitor.visitReference(it, it.referent, null, null, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params) }
        propKw?.let { return visitor.visitUniverse(it, 0, -1, null, null, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params) }
        underscore?.let { return visitor.visitInferHole(it, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params) }
        goal?.let { return visitor.visitGoal(it, it.defIdentifier?.textRepresentation(), it.expr, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params) }
        error("Incorrect expression: literal")
    }
}