package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType

class ArendIdentifierOrUnknown(node: ASTNode) : ArendCompositeElementImpl(node) {
    val defIdentifier: ArendDefIdentifier?
        get() = getChildOfType()
}