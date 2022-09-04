package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfType

class ArendAlias(node: ASTNode) : ArendCompositeElementImpl(node) {
    val aliasIdentifier: ArendAliasIdentifier?
        get() = getChildOfType()

    val prec: ArendPrec?
        get() = getChildOfType()
}