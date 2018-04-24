package org.vclang.annotation

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.vclang.VcLanguage
import org.vclang.psi.ext.VcReferenceElement

class VclangReferenceImporter : ReferenceImporter {
    override fun autoImportReferenceAt(editor: Editor, file: PsiFile, offset: Int): Boolean {
        if (!CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY)
            return false

        if (!file.viewProvider.languages.contains(VcLanguage))
            return false

        val element = file.findReferenceAt(offset)
        if (element is VcReferenceElement) {
            val fix = VclangImportHintAction(element)
            if (fix.isAvailable(element.project, editor, file)) {
                fix.doFix(editor, false)
                return true
            }
        }

        return false
    }

    override fun autoImportReferenceAtCursor(editor: Editor, file: PsiFile): Boolean {
        if (!file.viewProvider.languages.contains(JavaLanguage.INSTANCE)) {
            return false
        }

        val caretOffset = editor.caretModel.offset
        val document = editor.document
        val lineNumber = document.getLineNumber(caretOffset)
        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)

        val elements = CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset)
        for (element in elements) {
            if (element is VcReferenceElement) {
                val fix = VclangImportHintAction(element)
                if (fix.isAvailable(element.project, editor, file)) {
                    fix.doFix(editor, false)
                    return true
                }
            }
        }

        return false
    }
}