package org.arend.commenter

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import org.arend.psi.AREND_COMMENTS
import org.arend.psi.ArendElementTypes

class ArendCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix(): String = "--"

    override fun getBlockCommentPrefix(): String = "{-"

    override fun getBlockCommentSuffix(): String = "-}"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null

    override fun isDocumentationComment(element: PsiComment?) = element == ArendElementTypes.BLOCK_DOC_TEXT

    override fun getDocumentationCommentTokenType(): IElementType? = ArendElementTypes.BLOCK_DOC_TEXT

    override fun getLineCommentTokenType(): IElementType = ArendElementTypes.LINE_COMMENT

    override fun getBlockCommentTokenType(): IElementType = ArendElementTypes.BLOCK_COMMENT

    override fun getDocumentationCommentLinePrefix(): String? = "-"

    override fun getDocumentationCommentPrefix(): String? = "{- |"

    override fun getDocumentationCommentSuffix(): String? = "-}"
}
