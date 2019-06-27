package org.arend.highlight

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiUtil
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.ArendGroup

abstract class BasePassFactory : DirtyScopeTrackingHighlightingPassFactory {
    abstract fun createPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange): TextEditorHighlightingPass?

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (file !is ArendFile) {
            return null
        }

        val textRange = FileStatusMap.getDirtyTextRange(editor, passId)
        return if (textRange == null) {
            EmptyHighlightingPass(file.project, editor.document)
        } else {
            var psi = PsiUtil.getElementInclusiveRange(file, textRange)
            if (psi is PsiWhiteSpace || psi is PsiComment) {
                EmptyHighlightingPass(file.project, editor.document)
            } else {
                var group: ArendGroup = file
                while (psi is ArendCompositeElement && psi !is ArendFile) {
                    if (psi is ArendGroup) {
                        group = psi
                        break
                    }
                    psi = psi.parent
                }
                createPass(file, group, editor, textRange)
            }
        }
    }
}
