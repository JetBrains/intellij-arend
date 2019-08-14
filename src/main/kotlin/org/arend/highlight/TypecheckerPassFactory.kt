package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup

class TypecheckerPassFactory(highlightingPassRegistrar: TextEditorHighlightingPassRegistrar, silentTypecheckerPassFactory: SilentTypecheckerPassFactory) : BasePassFactory() {
    private val passId = highlightingPassRegistrar.registerTextEditorHighlightingPass(this, intArrayOf(silentTypecheckerPassFactory.passId), null, false, -1)

    override fun createPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange) =
        TypecheckerPass(file, editor, DefaultHighlightInfoProcessor())

    override fun getPassId() = passId
}