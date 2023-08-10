package org.arend.psi.ext

import org.arend.term.abs.AbstractLevelExpressionVisitor
import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfType
import org.arend.term.abs.Abstract


open class ArendLevelExpr(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.LevelExpression {
    override fun getData() = this

    override fun <P : Any?, R : Any?> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R = when (firstRelevantChild.elementType) {
        ArendElementTypes.SUC_KW -> visitor.visitSuc(this, childOfType<ArendAtomLevelExpr>(), params)
        ArendElementTypes.MAX_KW -> visitor.visitMax(this, childOfType<ArendAtomLevelExpr>(), childOfType<ArendAtomLevelExpr>(1), params)
        else -> error("Unknown ArendLevelExpr")
    }
}
