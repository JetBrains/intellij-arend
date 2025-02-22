package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfType
import org.arend.term.abs.AbstractExpressionVisitor


class ArendLiteral(node: ASTNode) : ArendExpr(node) {
    val goal: ArendGoal?
        get() = childOfType()

    val refIdentifier: ArendRefIdentifier?
        get() = childOfType()

    val ipName: ArendIPName?
        get() = childOfType()

    val dot: PsiElement?
        get() = findChildByType(DOT)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        ipName?.let {
            return visitor.visitReference(it, it.referent, it.fixity, null, null, params)
        }
        return when (val child = firstRelevantChild) {
            is ArendRefIdentifier -> visitor.visitReference(child, child.referent, null, null, null, params)
            is ArendGoal -> visitor.visitGoal(child, child.defIdentifier?.refName, child.expr, params)
            else -> when (child.elementType) {
                PROP_KW -> visitor.visitUniverse(this, 0, -1, null, null, params)
                UNDERSCORE -> visitor.visitInferHole(this, params)
                APPLY_HOLE -> visitor.visitApplyHole(this, params)
                STRING -> visitor.visitStringLiteral(this, child!!.text.removeSurrounding("\""), params)
                else -> error("Incorrect expression: literal")
            }
        }
    }
}