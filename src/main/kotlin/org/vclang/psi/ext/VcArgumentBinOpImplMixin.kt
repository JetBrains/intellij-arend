package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcArgumentBinOp


abstract class VcArgumentBinOpImplMixin(node: ASTNode) : VcExprImplMixin(node), VcArgumentBinOp {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R =
        visitor.visitApp(this, atomFieldsAcc, argumentList, params)
}