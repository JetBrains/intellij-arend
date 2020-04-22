package org.arend.injection

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.highlight.ArendHighlightingColors
import org.arend.psi.*

class InjectionHighlightingPass(val file: PsiInjectionTextFile, private val editor: Editor)
    : TextEditorHighlightingPass(file.project, editor.document, false) {

    private val holder = AnnotationHolderImpl(AnnotationSession(file))

    override fun doCollectInformation(progress: ProgressIndicator) {
        val files = (file.firstChild as? PsiInjectionText)?.let { InjectedLanguageManager.getInstance(file.project).getInjectedPsiFiles(it) } ?: return
        for (pair in files) {
            val visitor = object : ArendVisitor() {
                private fun toHostTextRange(range: TextRange) = range.shiftRight(pair.second.startOffset - ArendLanguageInjector.PREFIX.length)

                override fun visitIPName(o: ArendIPName) {
                    holder.createInfoAnnotation(toHostTextRange(o.textRange), null).textAttributes = ArendHighlightingColors.OPERATORS.textAttributesKey
                }

                override fun visitLongName(o: ArendLongName) {
                    val dot = (o.parent as? ArendLiteral)?.dot
                    val last = if (dot != null) dot else {
                        val refs = o.refIdentifierList
                        val ref = refs.lastOrNull()
                        if ((ref?.reference?.resolve() as? ArendDefinition)?.precedence?.isInfix == true) {
                            holder.createInfoAnnotation(toHostTextRange(ref.textRange), null).textAttributes = ArendHighlightingColors.OPERATORS.textAttributesKey
                        }
                        if (refs.size > 1) ref?.prevSibling else null
                    }
                    if (last != null) {
                        holder.createInfoAnnotation(toHostTextRange(TextRange(o.startOffset, last.endOffset)), null).textAttributes = ArendHighlightingColors.LONG_NAME.textAttributesKey
                    }
                }
            }
            PsiTreeUtil.processElements(pair.first) { it.accept(visitor); true }
        }
    }

    override fun doApplyInformationToEditor() {
        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        if (highlights.isEmpty()) {
            return
        }

        val textRange = file.textRange
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(myProject, editor.document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }
}