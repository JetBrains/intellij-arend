package org.arend.psi.doc

import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.local.TelescopeScope
import org.arend.parser.ParserMixin
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendStatement
import org.arend.term.abs.Abstract

class ArendDocComment(text: CharSequence?) : LazyParseablePsiElement(ParserMixin.DOC_COMMENT, text), PsiDocCommentBase {
    override fun getTokenType(): IElementType = ParserMixin.DOC_COMMENT

    override fun getOwner(): ArendGroup? {
        var sibling = nextSibling
        while (sibling is PsiWhiteSpace) {
            sibling = sibling.nextSibling
        }
        return (sibling as? ArendStatement)?.group
    }

    companion object {
        fun getScope(owner: PsiElement?): Scope? {
            val scope = (owner as? ArendCompositeElement)?.scope
            return if (owner is Abstract.ParametersHolder) TelescopeScope.make(scope ?: EmptyScope.INSTANCE, owner.parameters) else scope
        }
    }
}
