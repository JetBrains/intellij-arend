package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.getChildOfType
import org.arend.psi.getChildOfTypeStrict


class ArendLongNameExpr(node: ASTNode) : ArendExpr(node) {
    val longName: ArendLongName
        get() = getChildOfTypeStrict()

    val levelsExpr: ArendLevelsExpr?
        get() = getChildOfType()

    val pLevelExpr: ArendAtomOnlyLevelExpr?
        get() = getChildOfType()

    val hLevelExpr: ArendAtomOnlyLevelExpr?
        get() = getChildOfType(1)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val name = longName
        val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
        if (levels != null) {
            val pLevelExprs = levels.pLevelExprs
            val hLevelExprs = levels.hLevelExprs
            if (pLevelExprs != null && hLevelExprs != null) {
                return visitor.visitReference(name, name.referent, null, pLevelExprs.maybeAtomLevelExpr?.atomLevelExpr?.let { listOf(it) } ?: pLevelExprs.levelExprList, hLevelExprs.maybeAtomLevelExpr?.atomLevelExpr?.let { listOf(it) } ?: hLevelExprs.levelExprList, params)
            }
        }
        return visitor.visitReference(name, name.referent, null, pLevelExpr?.let { listOf(it) }, hLevelExpr?.let { listOf(it) }, params)
    }
}