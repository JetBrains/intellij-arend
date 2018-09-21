package org.arend.commenter

import com.intellij.lang.Commenter

class ArendCommenter : Commenter {

    override fun getLineCommentPrefix(): String? = "--"

    override fun getBlockCommentPrefix(): String? = "{-"

    override fun getBlockCommentSuffix(): String? = "-}"

    override fun getCommentedBlockCommentPrefix(): String? = null

    override fun getCommentedBlockCommentSuffix(): String? = null
}
