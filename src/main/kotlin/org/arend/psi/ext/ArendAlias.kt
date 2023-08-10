package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType

class ArendAlias(node: ASTNode) : ArendCompositeElementImpl(node) {
    val aliasIdentifier: ArendAliasIdentifier?
        get() = childOfType()

    val prec: ArendPrec?
        get() = childOfType()
}