package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLongNameExpr


abstract class ArendLongNameExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLongNameExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val name = longName
        val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
        if (levels != null) {
            levels.propKw?.let { return visitor.visitReference(name, name.referent, 0, -1, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params) }
            val levelExprList = levels.maybeAtomLevelExprList
            if (levelExprList.size == 2) {
                return visitor.visitReference(name, name.referent, levelExprList[0]?.atomLevelExpr, levelExprList[1]?.atomLevelExpr, if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
            }
        }
        val levelExprList = atomOnlyLevelExprList
        return visitor.visitReference(name, name.referent, levelExprList.getOrNull(0), levelExprList.getOrNull(1), if (visitor.visitErrors()) org.arend.psi.ext.getErrorData(this) else null, params)
    }
}