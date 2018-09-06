package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdLongNameExpr
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor


abstract class ArdLongNameExprImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdLongNameExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val name = longName
        val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
        if (levels != null) {
            levels.propKw?.let { return visitor.visitReference(name, name.referent, 0, -1, if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
            val levelExprList = levels.atomLevelExprList
            if (levelExprList.size == 2) {
                return visitor.visitReference(name, name.referent, levelExprList[0], levelExprList[1], if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
            }
        }
        val levelExprList = atomOnlyLevelExprList
        return visitor.visitReference(name, name.referent, levelExprList.getOrNull(0), levelExprList.getOrNull(1), if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params)
    }
}