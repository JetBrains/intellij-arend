package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup

class ArendHighlightingPassFactory(highlightingPassRegistrar: TextEditorHighlightingPassRegistrar) : BasePassFactory() {
    private val passId = highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, -1)

    override fun createPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange) =
        ArendHighlightingPass(file, group, editor, textRange, DefaultHighlightInfoProcessor())

    override fun getPassId() = passId
}