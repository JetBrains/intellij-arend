package org.arend.injection

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.highlight.ArendHighlightingColors
import org.arend.psi.*
import org.arend.psi.ext.impl.ReferableAdapter
import java.util.ArrayList

class InjectionHighlightingPass(val file: PsiInjectionTextFile, private val editor: Editor)
    : TextEditorHighlightingPass(file.project, editor.document, false) {

    private val highlights = ArrayList<HighlightInfo>()

    override fun doCollectInformation(progress: ProgressIndicator) {
        val manager = InjectedLanguageManager.getInstance(file.project)
        val files = (file.firstChild as? PsiInjectionText)?.let { manager.getInjectedPsiFiles(it) } ?: return
        for (pair in files) {
            val visitor = object : ArendVisitor() {
                private fun toHostTextRange(range: TextRange) = manager.injectedToHost(pair.first, range)

                private fun addHighlightInfo(range: TextRange, colors: ArendHighlightingColors) {
                    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(toHostTextRange(range)).textAttributes(colors.textAttributesKey).create()
                    if (info != null) {
                        highlights.add(info)
                    }
                }

                override fun visitIPName(o: ArendIPName) {
                    addHighlightInfo(o.textRange, ArendHighlightingColors.OPERATORS)
                }

                override fun visitLongName(o: ArendLongName) {
                    val dot = (o.parent as? ArendLiteral)?.dot
                    val last = if (dot != null) dot else {
                        val refs = o.refIdentifierList
                        val ref = refs.lastOrNull()
                        val resolved = ref?.reference?.let { runReadAction { it.resolve() } } as? ReferableAdapter<*>
                        if (resolved != null) {
                            val alias = resolved.getAlias()
                            if (ReferableAdapter.calcPrecedence(alias?.prec).isInfix || alias == null && ReferableAdapter.calcPrecedence(resolved.getPrec()).isInfix) {
                                addHighlightInfo(ref.textRange, ArendHighlightingColors.OPERATORS)
                            }
                        }
                        if (refs.size > 1) ref?.prevSibling else null
                    }
                    if (last != null) {
                        addHighlightInfo(TextRange(o.startOffset, last.endOffset), ArendHighlightingColors.LONG_NAME)
                    }
                }
            }
            PsiTreeUtil.processElements(pair.first) { it.accept(visitor); true }
        }
    }

    override fun doApplyInformationToEditor() {
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