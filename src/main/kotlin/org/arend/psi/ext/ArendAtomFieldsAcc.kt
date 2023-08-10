package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfTypeStrict
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.AbstractExpressionVisitor


class ArendAtomFieldsAcc(node: ASTNode) : ArendExpr(node) {
    val atom: ArendAtom
        get() = childOfTypeStrict()

    val numberList: List<PsiElement>
        get() = getChildrenOfType(ArendElementTypes.NUMBER)

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val fieldAccs = numberList.map { it.text.toInt() }
        return if (fieldAccs.isEmpty()) {
            atom.accept(visitor, params)
        } else {
            visitor.visitFieldAccs(this, atom, fieldAccs, params)
        }
    }
}