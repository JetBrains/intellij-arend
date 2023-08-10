package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType

class ArendDataBody(node: ASTNode) : ArendCompositeElementImpl(node) {
    val elim: ArendElim?
        get() = childOfType()

    val constructorClauseList: List<ArendConstructorClause>
        get() = getChildrenOfType()

    val constructorList: List<ArendConstructor>
        get() = getChildrenOfType()
}