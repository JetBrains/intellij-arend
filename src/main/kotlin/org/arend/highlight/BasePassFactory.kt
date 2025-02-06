package org.arend.highlight

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

abstract class BasePassFactory<T : PsiFile>(private val clazz: Class<T>) : DirtyScopeTrackingHighlightingPassFactory {
    abstract fun createPass(file: T, editor: Editor, textRange: TextRange): TextEditorHighlightingPass?

    protected open fun allowWhiteSpaces() = false

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!clazz.isInstance(file)) {
            return null
        }

        val textRange = FileStatusMap.getDirtyTextRange(editor.document, file, passId)
        return if (textRange != null) createPass(clazz.cast(file), editor, textRange) else EmptyHighlightingPass(file.project, editor.document)
    }
}
