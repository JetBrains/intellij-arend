package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcArgumentAppExpr


abstract class VcArgumentAppExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcArgumentAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        atomFieldsAcc?.let {
            val args = argumentList
            return if (args.isEmpty()) it.accept(visitor, params) else visitor.visitBinOpSequence(this, it, args, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
        }
        longName?.let { name ->
            val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
            if (levels != null) {
                levels.propKw?.let { return visitor.visitReference(name, name.referent, 0, -1, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params) }
                val levelExprList = levels.atomLevelExprList
                if (levelExprList.size == 2) {
                    return visitor.visitReference(name, name.referent, levelExprList[0], levelExprList[1], if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
                }
            }
            val levelExprList = atomOnlyLevelExprList
            return visitor.visitReference(name, name.referent, levelExprList.getOrNull(0), levelExprList.getOrNull(1), if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
        }
        error("Incomplete expression: " + this)
    }
}