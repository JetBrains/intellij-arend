package org.arend.psi.doc

import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.arend.parser.ParserMixin
import org.arend.psi.ArendDefinition

class ArendDocComment(text: CharSequence?) : LazyParseablePsiElement(ParserMixin.DOC_COMMENT, text), PsiDocCommentBase {
    override fun getTokenType(): IElementType = ParserMixin.DOC_COMMENT

    override fun getOwner(): ArendDefinition? {
        var sibling = nextSibling
        while (sibling is PsiWhiteSpace) {
            sibling = sibling.nextSibling
        }
        return sibling as? ArendDefinition
    }
}
