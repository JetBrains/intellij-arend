package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendLongNameExpr


abstract class ArendLongNameExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendLongNameExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val name = longName
        val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
        if (levels != null) {
            val levelExprsList = levels.maybeAtomLevelExprsList
            if (levelExprsList.size == 2) {
                return visitor.visitReference(name, name.referent, null, levelExprsList[0]?.atomLevelExpr?.let { listOf(it) } ?: levelExprsList[0]?.levelExprList, levelExprsList[1]?.atomLevelExpr?.let { listOf(it) } ?: levelExprsList[1]?.levelExprList, params)
            }
        }
        val levelExprList = atomOnlyLevelExprList
        return visitor.visitReference(name, name.referent, null, levelExprList.getOrNull(0)?.let { listOf(it) }, levelExprList.getOrNull(1)?.let { listOf(it) }, params)
    }
}