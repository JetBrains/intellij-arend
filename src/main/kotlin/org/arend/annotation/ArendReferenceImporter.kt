package org.arend.annotation

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.ReferenceImporter
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.arend.ArendLanguage
import org.arend.psi.ext.ArendReferenceElement

class ArendReferenceImporter : ReferenceImporter {
    override fun autoImportReferenceAt(editor: Editor, file: PsiFile, offset: Int): Boolean {
        if (!CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY)
            return false

        if (!file.viewProvider.languages.contains(ArendLanguage.INSTANCE))
            return false

        val element = file.findReferenceAt(offset)
        if (element is ArendReferenceElement) {
            val fix = ArendImportHintAction(element)
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
            if (element is ArendReferenceElement) {
                val fix = ArendImportHintAction(element)
                if (fix.isAvailable(element.project, editor, file)) {
                    fix.doFix(editor, false)
                    return true
                }
            }
        }

        return false
    }
}