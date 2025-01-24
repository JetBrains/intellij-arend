package org.arend.scratch

import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.CommonDataKeys.CARET
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.util.FileUtils
import org.arend.util.addImports
import org.jetbrains.kotlin.idea.statistics.KotlinCreateFileFUSCollector.logFileTemplate

class ArendScratchFileCreationHelper : ScratchFileCreationHelper() {
    override fun prepareText(project: Project, context: Context, dataContext: DataContext): Boolean {
        logFileTemplate("Arend Scratch")
        context.fileExtension = SCRATCH_SUFFIX

        val text = context.text
        val arendFile = dataContext.getData(PSI_FILE) as? ArendFile?
            ?: return super.prepareText(project, context, dataContext)

        val start = dataContext.getData(CARET)?.selectionStart ?: 0
        val end = dataContext.getData(CARET)?.selectionEnd ?: arendFile.textLength

        val visitor = object : PsiRecursiveElementVisitor() {
            val referables = mutableSetOf<PsiLocatedReferable>()

            private fun checkElement(referable: PsiElement): Boolean {
                return !(referable.textRange.startOffset < start || referable.textRange.endOffset > end)
            }

            override fun visitElement(element: PsiElement) {
                if (checkElement(element) && element is ArendReferenceContainer) {
                    (element.resolve as? PsiLocatedReferable?)?.let { referables.add(it) }
                }
                super.visitElement(element)
            }
        }

        arendFile.accept(visitor)
        context.text = StringBuilder().addImports(project, visitor.referables).toString() + text

        return super.prepareText(project, context, dataContext)
    }
}
