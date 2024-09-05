package org.arend.commenter

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import org.arend.parser.ParserMixin
import org.arend.psi.ArendElementTypes
import org.arend.psi.doc.ArendDocComment

class ArendCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix(): String = "-- "

    override fun getBlockCommentPrefix(): String = "{-"

    override fun getBlockCommentSuffix(): String = "-}"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null

    override fun isDocumentationComment(element: PsiComment?) = element is ArendDocComment

    override fun getDocumentationCommentTokenType(): IElementType? = ParserMixin.DOC_COMMENT

    override fun getLineCommentTokenType(): IElementType = ArendElementTypes.LINE_COMMENT

    override fun getBlockCommentTokenType(): IElementType = ArendElementTypes.BLOCK_COMMENT

    override fun getDocumentationCommentLinePrefix(): String = "-"

    override fun getDocumentationCommentPrefix(): String = "{- |"

    override fun getDocumentationCommentSuffix(): String = "-}"
}
