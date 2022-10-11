package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.firstRelevantChild
import org.arend.psi.hasChildOfType
import org.arend.term.abs.Abstract

class ArendAppPrefix(node: ASTNode) : ArendCompositeElementImpl(node) {
    val isNew: Boolean
        get() = firstRelevantChild.elementType == NEW_KW

    val evalKind: Abstract.EvalKind?
        get() = if (hasChildOfType(EVAL_KW)) Abstract.EvalKind.EVAL else
            when (firstRelevantChild.elementType) {
                PEVAL_KW -> Abstract.EvalKind.PEVAL
                BOX_KW -> Abstract.EvalKind.BOX
                else -> null
            }
}