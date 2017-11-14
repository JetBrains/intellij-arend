package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcArgumentBinOp


abstract class VcArgumentBinOpImplMixin(node: ASTNode) : VcExprImplMixin(node), VcArgumentBinOp {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        atomFieldsAcc?.let { return visitor.visitApp(this, it, argumentList, params) }
        longName?.let { name ->
            val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
            levels?.propKw?.let { return visitor.visitReference(name, name.referent, 0, -1, params) }
            levels?.maybeAtomLevelExprList?.let { if (it.size == 2) {
                return visitor.visitReference(name, name.referent, it[0].atomLevelExpr, it[1].atomLevelExpr, params)
            } }
            val levelExprList = atomOnlyLevelExprList
            return visitor.visitReference(name, name.referent, levelExprList.getOrNull(0), levelExprList.getOrNull(1), params)
        }
        error("Incomplete expression: " + this)
    }
}