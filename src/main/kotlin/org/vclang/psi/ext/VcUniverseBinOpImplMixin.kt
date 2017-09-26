package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcUniverseBinOp


abstract class VcUniverseBinOpImplMixin(node: ASTNode) : VcExprImplMixin(node), VcUniverseBinOp {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R {
        val levelExprs = atomLevelExprList
        return visitor.visitUniverse(this, universe.text.substring("\\Type".length).toIntOrNull(), null, levelExprs.getOrNull(0), levelExprs.getOrNull(1), params)
    }
}