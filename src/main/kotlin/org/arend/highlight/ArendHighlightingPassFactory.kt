package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile

class ArendHighlightingPassFactory(highlightingPassRegistrar: TextEditorHighlightingPassRegistrar) : TextEditorHighlightingPassFactory {
    init {
        highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor) =
            (file as? ArendFile)?.let { ArendHighlightingPass(it, editor, TextRange.EMPTY_RANGE, DefaultHighlightInfoProcessor()) }
}