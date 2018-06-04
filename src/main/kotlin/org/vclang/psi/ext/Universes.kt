package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.term.Fixity
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.*


private fun <P : Any?, R : Any?> acceptSet(data: Any, setElem: PsiElement, pLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, setElem.text.substring("\\Set".length).toIntOrNull(), 0, pLevel, null, if (visitor.visitErrors() && setElem is VcCompositeElement) org.vclang.psi.ext.getErrorData(setElem) else null, params)

private fun <P : Any?, R : Any?> acceptUniverse(data: Any, universeElem: PsiElement, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, universeElem.text.substring("\\Type".length).toIntOrNull(), null, pLevel, hLevel, if (visitor.visitErrors() && universeElem is VcCompositeElement) org.vclang.psi.ext.getErrorData(universeElem) else null, params)

private fun <P : Any?, R : Any?> acceptTruncated(data: Any, truncatedElem: PsiElement, pLevel: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
    val uniText = truncatedElem.text
    val index = uniText.indexOf('-')
    val hLevelNum = when {
        uniText.startsWith("\\oo-")      -> Abstract.INFINITY_LEVEL
        index >= 0 && uniText[0] == '\\' -> uniText.substring(1, index).toIntOrNull()
        else                             -> null
    }
    val pLevelNum = if (hLevelNum != null) uniText.substring(index + "-Type".length).toIntOrNull() else null
    return visitor.visitUniverse(data, pLevelNum, hLevelNum, pLevel, null, if (visitor.visitErrors() && truncatedElem is VcCompositeElement) org.vclang.psi.ext.getErrorData(truncatedElem) else null, params)
}


abstract class VcSetUniverseAppExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcSetUniverseAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptSet(this, set, atomLevelExpr, visitor, params)
}

abstract class VcTruncatedUniverseAppExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcTruncatedUniverseAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTruncated(this, truncatedUniverse, atomLevelExpr, visitor, params)
}

abstract class VcUniverseAppExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcUniverseAppExpr {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val levelExprs = atomLevelExprList
        return acceptUniverse(this, universe, levelExprs.getOrNull(0), levelExprs.getOrNull(1), visitor, params)
    }
}

abstract class VcUniverseAtomImplMixin(node: ASTNode) : VcExprImplMixin(node), VcUniverseAtom {
    override fun isExplicit(): Boolean = true

    override fun getFixity(): Fixity = Fixity.NONFIX

    override fun getExpression(): VcExpr = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        set?.let { return acceptSet(this, it, null, visitor, params) }
        universe?.let { return acceptUniverse(this, it, null, null, visitor, params) }
        truncatedUniverse?.let { return acceptTruncated(this, it, null, visitor, params) }
        error("Incorrect expression: universe")
    }
}
