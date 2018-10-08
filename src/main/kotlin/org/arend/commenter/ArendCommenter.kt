package org.arend.commenter

import com.intellij.lang.CodeDocumentationAwareCommenter
import com.intellij.psi.PsiComment
import com.intellij.psi.tree.IElementType
import org.arend.psi.ArendElementTypes

class ArendCommenter : CodeDocumentationAwareCommenter {
    override fun getLineCommentPrefix(): String = "--"

    override fun getBlockCommentPrefix(): String = "{-"

    override fun getBlockCommentSuffix(): String = "-}"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null

    override fun isDocumentationComment(element: PsiComment?) = false

    override fun getDocumentationCommentTokenType(): IElementType? = null

    override fun getLineCommentTokenType(): IElementType = ArendElementTypes.LINE_COMMENT

    override fun getBlockCommentTokenType(): IElementType = ArendElementTypes.BLOCK_COMMENT

    override fun getDocumentationCommentLinePrefix(): String? = null

    override fun getDocumentationCommentPrefix(): String? = null

    override fun getDocumentationCommentSuffix(): String? = null
}
