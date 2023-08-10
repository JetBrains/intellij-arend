package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


private fun <P : Any?, R : Any?> acceptSet(data: ArendCompositeElement, setElem: PsiElement, pLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, setElem.text.substring("\\Set".length).toIntOrNull(), 0, pLevel, null, params)

private fun <P : Any?, R : Any?> acceptUniverse(data: ArendCompositeElement, universeElem: PsiElement, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, universeElem.text.substring("\\Type".length).toIntOrNull(), null, pLevel, hLevel, params)

private fun <P : Any?, R : Any?> acceptTruncated(data: ArendCompositeElement, truncatedElem: PsiElement, pLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
    val uniText = truncatedElem.text
    val index = uniText.indexOf('T')
    val hLevelNum = when {
        uniText.startsWith("\\oo-") || uniText.startsWith("\\h") -> Abstract.INFINITY_LEVEL
        index > 0 && uniText[0] == '\\'                          -> uniText.substring(1, index - 1).toIntOrNull()
        else                                                     -> null
    }
    val pLevelNum = if (hLevelNum != null) uniText.substring(index + "Type".length).toIntOrNull() else null
    return visitor.visitUniverse(data, pLevelNum, hLevelNum, pLevel, null, params)
}


class ArendSetUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val maybeAtomLevelExpr: ArendMaybeAtomLevelExpr?
        get() = childOfType()

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptSet(this, notNullChild(firstRelevantChild), maybeAtomLevelExpr?.atomLevelExpr, visitor, params)
}

class ArendTruncatedUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val maybeAtomLevelExpr: ArendMaybeAtomLevelExpr?
        get() = childOfType()

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTruncated(this, notNullChild(firstRelevantChild), maybeAtomLevelExpr?.atomLevelExpr, visitor, params)
}

class ArendUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    fun getMaybeAtomLevelExpr(index: Int): ArendMaybeAtomLevelExpr? = childOfType(index)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptUniverse(this, notNullChild(firstRelevantChild), getMaybeAtomLevelExpr(0)?.atomLevelExpr, getMaybeAtomLevelExpr(1)?.atomLevelExpr, visitor, params)
}

class ArendUniverseAtom(node: ASTNode) : ArendExpr(node), ArendArgument {
    override fun isExplicit(): Boolean = true

    override fun isVariable() = false

    override fun getExpression(): ArendExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val child = firstRelevantChild
        return when (child.elementType) {
            SET -> acceptSet(this, child!!, null, visitor, params)
            UNIVERSE -> acceptUniverse(this, child!!, null, null, visitor, params)
            TRUNCATED_UNIVERSE -> acceptTruncated(this, child!!, null, visitor, params)
            else -> error("Incorrect expression: universe")
        }
    }
}
