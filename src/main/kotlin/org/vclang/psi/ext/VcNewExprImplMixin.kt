package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcNewExpr

abstract class VcNewExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcNewExpr, Abstract.ClassReferenceHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitClassExt(this, newKw != null, binOpArg, implementStatementList, params)

    override fun getClassReference(): ClassReferable? = null // TODO[abstract]: Replace expression with a reference
}
