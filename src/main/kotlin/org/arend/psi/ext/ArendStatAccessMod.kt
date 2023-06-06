package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfTypeStrict

class ArendStatAccessMod(node: ASTNode) : ArendSourceNodeImpl(node) {
    val accessModifier
        get() = getChildOfTypeStrict<ArendAccessMod>().accessModifier
}