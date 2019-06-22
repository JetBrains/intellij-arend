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
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile

class ArendHighlightingPassFactory(private val project: Project, highlightingPassRegistrar: TextEditorHighlightingPassRegistrar) : DirtyScopeTrackingHighlightingPassFactory {
    private val passId = highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, -1)

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (file !is ArendFile) {
            return null
        }

        val textRange = FileStatusMap.getDirtyTextRange(editor, passId)
        return if (textRange == null) {
            object : TextEditorHighlightingPass(project, editor.document, false) {
                override fun doCollectInformation(progress: ProgressIndicator) {}

                override fun doApplyInformationToEditor() {
                    DaemonCodeAnalyzerEx.getInstanceEx(myProject).fileStatusMap.markFileUpToDate(document!!, id)
                }
            }
        } else {
            ArendHighlightingPass(file, editor, textRange, DefaultHighlightInfoProcessor())
        }
    }

    override fun getPassId() = passId
}