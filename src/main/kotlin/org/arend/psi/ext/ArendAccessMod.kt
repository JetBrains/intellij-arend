package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendElementTypes.PRIVATE_KW
import org.arend.psi.ArendElementTypes.PROTECTED_KW
import org.arend.psi.hasChildOfType
import org.arend.term.group.AccessModifier

class ArendAccessMod(node: ASTNode) : ArendSourceNodeImpl(node) {
    val accessModifier: AccessModifier
        get() = when {
            hasChildOfType(PRIVATE_KW) -> AccessModifier.PRIVATE
            hasChildOfType(PROTECTED_KW) -> AccessModifier.PROTECTED
            else -> AccessModifier.PUBLIC
        }
}