package org.arend.highlight

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiUtil
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.ArendGroup

class ArendHighlightingPassFactory(private val project: Project, highlightingPassRegistrar: TextEditorHighlightingPassRegistrar) : DirtyScopeTrackingHighlightingPassFactory {
    private val passId = highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, -1)

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (file !is ArendFile) {
            return null
        }

        val textRange = FileStatusMap.getDirtyTextRange(editor, passId)
        return if (textRange == null) {
            EmptyHighlightingPass(project, editor.document)
        } else {
            var psi = PsiUtil.getElementInclusiveRange(file, textRange)
            if (psi is PsiWhiteSpace || psi is PsiComment) {
                EmptyHighlightingPass(project, editor.document)
            } else {
                var group: ArendGroup = file
                while (psi is ArendCompositeElement && psi !is ArendFile) {
                    if (psi is ArendGroup) {
                        group = psi
                        break
                    }
                    psi = psi.parent
                }
                ArendHighlightingPass(file, group, editor, textRange, DefaultHighlightInfoProcessor())
            }
        }
    }

    override fun getPassId() = passId
}