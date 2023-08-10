package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfTypeStrict
import org.arend.psi.getChildrenOfType

class ArendStatAccessMod(node: ASTNode) : ArendSourceNodeImpl(node) {
    val accessModifier
        get() = childOfTypeStrict<ArendAccessMod>().accessModifier

    val statList: List<ArendStat>
        get() = getChildrenOfType()
}