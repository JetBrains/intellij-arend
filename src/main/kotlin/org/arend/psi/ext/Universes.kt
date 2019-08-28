package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor


private fun <P : Any?, R : Any?> acceptSet(data: ArendCompositeElement, setElem: PsiElement, pLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, setElem.text.substring("\\Set".length).toIntOrNull(), 0, pLevel, null, if (visitor.visitErrors()) getErrorData(data) else null, params)

private fun <P : Any?, R : Any?> acceptUniverse(data: ArendCompositeElement, universeElem: PsiElement, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, universeElem.text.substring("\\Type".length).toIntOrNull(), null, pLevel, hLevel, if (visitor.visitErrors()) getErrorData(data) else null, params)

private fun <P : Any?, R : Any?> acceptTruncated(data: ArendCompositeElement, truncatedElem: PsiElement, pLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
    val uniText = truncatedElem.text
    val index = uniText.indexOf('-')
    val hLevelNum = when {
        uniText.startsWith("\\oo-")      -> Abstract.INFINITY_LEVEL
        index >= 0 && uniText[0] == '\\' -> uniText.substring(1, index).toIntOrNull()
        else                             -> null
    }
    val pLevelNum = if (hLevelNum != null) uniText.substring(index + "-Type".length).toIntOrNull() else null
    return visitor.visitUniverse(data, pLevelNum, hLevelNum, pLevel, null, if (visitor.visitErrors()) getErrorData(data) else null, params)
}


abstract class ArendSetUniverseAppExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendSetUniverseAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptSet(this, set, maybeAtomLevelExpr?.atomLevelExpr, visitor, params)
}

abstract class ArendTruncatedUniverseAppExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendTruncatedUniverseAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTruncated(this, truncatedUniverse, maybeAtomLevelExpr?.atomLevelExpr, visitor, params)
}

abstract class ArendUniverseAppExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendUniverseAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val list = maybeAtomLevelExprList
        return acceptUniverse(this, universe, list.getOrNull(0)?.atomLevelExpr, list.getOrNull(1)?.atomLevelExpr, visitor, params)
    }
}

abstract class ArendUniverseAtomImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendUniverseAtom {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): ArendExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        set?.let { return acceptSet(this, it, null, visitor, params) }
        universe?.let { return acceptUniverse(this, it, null, null, visitor, params) }
        truncatedUniverse?.let { return acceptTruncated(this, it, null, visitor, params) }
        error("Incorrect expression: universe")
    }
}
