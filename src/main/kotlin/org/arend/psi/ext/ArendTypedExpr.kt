package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType

class ArendTypedExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val identifierOrUnknownList: List<ArendIdentifierOrUnknown>
        get() = getChildrenOfType()

    val type: ArendExpr?
        get() = childOfType()
}