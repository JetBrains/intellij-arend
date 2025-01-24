package org.arend.scratch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.ScratchFileLanguageProvider
import org.jetbrains.kotlin.idea.scratch.SequentialScratchExecutor

class ArendScratchFileLanguageProvider : ScratchFileLanguageProvider() {

    override fun createFile(project: Project, file: VirtualFile) = ArendScratchFile(project, file)

    override fun createReplExecutor(file: ScratchFile): SequentialScratchExecutor = ArendScratchReplExecutor(file)

    override fun createCompilingExecutor(file: ScratchFile): ScratchExecutor = ArendScratchCompilingExecutor(file)
}
