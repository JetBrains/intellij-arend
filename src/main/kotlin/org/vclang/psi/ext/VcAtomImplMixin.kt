package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAtom
import java.math.BigInteger


abstract class VcAtomImplMixin(node: ASTNode) : VcExprImplMixin(node), VcAtom {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        literal?.let { return it.accept(visitor, params) }
        number?.text?.let { return visitor.visitNumericLiteral(this, BigInteger(it), params) }
        tuple?.let { return visitor.visitTuple(this, it.exprList, params) }
        atomModuleCall?.let { return visitor.visitModuleCall(this, ModulePath(it.moduleName.moduleNamePartList.map { it.refIdentifier.referenceName ?: it.text }), params) }
        error("Incorrect expression: atom")
    }
}