package org.arend.psi.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.arend.psi.ArendElementTypes
import org.arend.psi.parser.impl.ArendPatternImpl


object ArendPsiCreator {
    fun createPsi(node: ASTNode) : PsiElement {
        return when (node.elementType) {
            ArendElementTypes.PATTERN -> ArendPatternImpl(node)
            else -> error("Unknown element type: ${node.elementType}")
        }
    }

}