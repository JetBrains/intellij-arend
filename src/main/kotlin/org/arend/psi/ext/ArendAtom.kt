package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfType
import org.arend.term.abs.AbstractExpressionVisitor
import java.math.BigInteger


class ArendAtom(node: ASTNode) : ArendExpr(node) {
    val literal: ArendLiteral?
        get() = childOfType()

    val tuple: ArendTuple?
        get() = childOfType()

    val thisKw: PsiElement?
        get() = findChildByType(THIS_KW)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        when (val child = firstRelevantChild) {
            is ArendLiteral -> child.accept(visitor, params)
            is ArendTuple -> child.accept(visitor, params)
            else -> when (child.elementType) {
                NUMBER, NEGATIVE_NUMBER -> visitor.visitNumericLiteral(this, BigInteger(child!!.text), params)
                THIS_KW -> visitor.visitThis(this, params)
                else -> error("Incorrect expression: atom")
            }
        }
}