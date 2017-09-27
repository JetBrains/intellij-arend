package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcClause
import org.vclang.psi.VcExpr
import org.vclang.psi.VcFunctionBody


abstract class VcFunctionBodyImplMixin(node: ASTNode) : VcCompositeElementImpl(node), VcFunctionBody {
    override fun getData(): VcFunctionBodyImplMixin = this

    override fun getTerm(): Abstract.Expression? = expr

    override fun getEliminatedExpressions(): List<VcExpr> = elim?.atomFieldsAccList ?: emptyList()

    override fun getClauses(): List<VcClause> = functionClauses?.clauseList ?: emptyList()
}