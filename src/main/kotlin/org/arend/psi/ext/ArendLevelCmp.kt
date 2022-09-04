package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes
import org.arend.psi.firstRelevantChild

class ArendLevelCmp(node: ASTNode) : ArendCompositeElementImpl(node) {
    val isIncreasing: Boolean
        get() = firstRelevantChild.elementType != ArendElementTypes.GREATER_OR_EQUALS
}