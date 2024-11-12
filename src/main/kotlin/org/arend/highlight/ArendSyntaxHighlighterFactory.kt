package org.arend.highlight

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ArendSyntaxHighlighterFactory : SyntaxHighlighterFactory(), SyntaxHighlighterProvider {
    override fun getSyntaxHighlighter(
            project: Project?,
            virtualFile: VirtualFile?
    ): SyntaxHighlighter = ArendSyntaxHighlighter()

    override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter {
        return ArendSyntaxHighlighter()
    }
}
