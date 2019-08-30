package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendSectionExpr
import org.arend.term.abs.AbstractExpressionVisitor


abstract class ArendSectionExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendSectionExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitSection(this, (postfixArgument as ArendPostfixArgumentImplMixin).referent, newExpr, if (visitor.visitErrors()) getErrorData(this) else null, params)
}
