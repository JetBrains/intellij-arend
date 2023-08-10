package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType

class ArendIdentifierOrUnknown(node: ASTNode) : ArendCompositeElementImpl(node) {
    val defIdentifier: ArendDefIdentifier?
        get() = childOfType()
}