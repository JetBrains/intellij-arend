package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdAtomFieldsAcc
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdAtomFieldsAccImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdAtomFieldsAcc {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val fieldAccs = fieldAccList.mapNotNull { it.number?.text?.toIntOrNull() }
        return if (fieldAccs.isEmpty()) atom.accept(visitor, params) else visitor.visitFieldAccs(this, atom, fieldAccs, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
    }
}