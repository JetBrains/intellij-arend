package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcNewExpr
import org.vclang.resolving.NamespaceProvider

abstract class VcNewExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcNewExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitClassExt(this, newKw != null, binOpArg, implementStatements?.implementStatementList, params)

    /* TODO[abstract]
    override val namespace: Namespace
        get() {
            val parentScope = (parent as? VcCompositeElement)?.scope ?: EmptyScope
            return NamespaceProvider.forExpression(this, parentScope)
        }
    */
}
