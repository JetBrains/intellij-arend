package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.LongUnresolvedReference
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcLiteral


abstract class VcLiteralImplMixin(node: ASTNode) : VcExprImplMixin(node), VcLiteral {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        longName?.let { return visitor.visitReference(it, LongUnresolvedReference.make(it, it.prefixName.textRepresentation(), it.refIdentifierList.map { it.referenceName }), params) }
        propKw?.let { return visitor.visitUniverse(it, 0, -1, null, null, params) }
        underscore?.let { return visitor.visitInferHole(it, params) }
        goal?.let { return visitor.visitGoal(it, it.defIdentifier?.textRepresentation(), it.expr, params) }
        error("Incorrect expression: literal")
    }
}