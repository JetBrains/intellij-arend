package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAtomFieldsAcc


abstract class VcAtomFieldsAccImplMixin(node: ASTNode) : VcExprImplMixin(node), VcAtomFieldsAcc {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val fieldAccs = fieldAccList
        return if (fieldAccs.isEmpty()) atom.accept(visitor, params) else visitor.visitFieldAccs(this, atom, fieldAccs.map { it.number.text?.toIntOrNull() ?: 0 }, params)
    }
}