package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.psi.getChildrenOfType
import org.arend.term.Fixity
import org.arend.term.abs.AbstractExpressionVisitor


class ArendAtomFieldsAcc(node: ASTNode) : ArendExpr(node) {
    val atom: ArendAtom
        get() = childOfTypeStrict()

    val fieldAccList: List<ArendFieldAcc>
        get() = getChildrenOfType()

    val ipName: ArendIPName?
        get() = childOfType()

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val fieldAccs = fieldAccList
        val ipName = ipName
        return if (fieldAccs.isEmpty() && ipName == null) {
            atom.accept(visitor, params)
        } else {
            visitor.visitFieldAccs(this, atom, fieldAccs, ipName, ipName?.referenceName, ipName?.fixity == Fixity.INFIX, params)
        }
    }
}