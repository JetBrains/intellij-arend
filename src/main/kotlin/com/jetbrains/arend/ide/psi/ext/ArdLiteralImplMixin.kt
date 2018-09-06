package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdLiteral
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdLiteralImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdLiteral {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        longName?.let { return visitor.visitReference(it, it.referent, null, null, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
        propKw?.let { return visitor.visitUniverse(it, 0, -1, null, null, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
        underscore?.let { return visitor.visitInferHole(it, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
        goal?.let { return visitor.visitGoal(it, it.defIdentifier?.textRepresentation(), it.expr, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
        error("Incorrect expression: literal")
    }
}