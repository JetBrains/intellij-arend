package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.typechecking.typecheckable.provider.EmptyConcreteProvider

class ArendHighlightingPassFactory(highlightingPassRegistrar: TextEditorHighlightingPassRegistrar) : BasePassFactory() {
    private val passId = highlightingPassRegistrar.registerTextEditorHighlightingPass(this, null, null, false, -1)
    var concreteProvider: ConcreteProvider = EmptyConcreteProvider.INSTANCE

    override fun createPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange) =
        ArendHighlightingPass(this, file, group, editor, textRange, DefaultHighlightInfoProcessor())

    override fun getPassId() = passId
}