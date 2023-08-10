package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict


class ArendLongNameExpr(node: ASTNode) : ArendExpr(node) {
    val longName: ArendLongName
        get() = childOfTypeStrict()

    val levelsExpr: ArendLevelsExpr?
        get() = childOfType()

    val pLevelExpr: ArendAtomOnlyLevelExpr?
        get() = childOfType()

    val hLevelExpr: ArendAtomOnlyLevelExpr?
        get() = childOfType(1)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val name = longName
        val levels = generateSequence(levelsExpr) { it.levelsExpr }.lastOrNull()
        if (levels != null) {
            val pLevelExprs = levels.pLevelExprs
            val hLevelExprs = levels.hLevelExprs
            if (pLevelExprs != null && hLevelExprs != null) {
                return visitor.visitReference(name, name.referent, null, pLevelExprs.levelExprList, hLevelExprs.levelExprList, params)
            }
        }
        return visitor.visitReference(name, name.referent, null, pLevelExpr?.let { listOf(it) }, hLevelExpr?.let { listOf(it) }, params)
    }
}