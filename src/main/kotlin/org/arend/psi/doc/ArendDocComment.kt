package org.arend.psi.doc

import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement
import com.intellij.psi.tree.IElementType
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.prettyprinting.doc.LineDoc
import org.arend.parser.ParserMixin
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendStat

class ArendDocComment(text: CharSequence?) : LazyParseablePsiElement(ParserMixin.DOC_COMMENT, text), PsiDocCommentBase {
    override fun getTokenType(): IElementType = ParserMixin.DOC_COMMENT

    override fun getOwner(): ArendGroup? {
        var sibling = nextSibling
        while (sibling is PsiWhiteSpace) {
            sibling = sibling.nextSibling
        }
        return (sibling as? ArendStat)?.group
    }

    val doc: Doc
        get() {
            val lines = ArrayList<Doc>()
            var curLine = ArrayList<LineDoc>()
            var child = firstChild?.nextSibling
            while (child != null) {
                when {
                    child.node.elementType == ParserMixin.DOC_END -> {}
                    child is ArendDocReference -> curLine.add(DocFactory.refDoc(child.longName.referent))
                    child.node.elementType == ParserMixin.DOC_NEWLINE -> {
                        lines.add(DocFactory.hList(curLine))
                        curLine = ArrayList()
                    }
                    else -> curLine.add(DocFactory.text(child.text))
                }
                child = child.nextSibling
            }
            if (curLine.isNotEmpty()) lines.add(DocFactory.hList(curLine))
            return DocFactory.vList(lines)
        }
}
