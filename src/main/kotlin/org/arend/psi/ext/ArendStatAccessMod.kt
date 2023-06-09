package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildOfTypeStrict
import org.arend.psi.getChildrenOfType

class ArendStatAccessMod(node: ASTNode) : ArendSourceNodeImpl(node) {
    val accessModifier
        get() = getChildOfTypeStrict<ArendAccessMod>().accessModifier

    val statList: List<ArendStat>
        get() = getChildrenOfType()
}