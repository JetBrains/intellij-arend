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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.highlight.ArendHighlightingColors
import org.arend.psi.ext.ArendIPName
import org.arend.psi.ext.ArendLiteral
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ReferableBase
import java.util.ArrayList

class InjectionHighlightingPass(val file: PsiInjectionTextFile, private val editor: Editor)
    : TextEditorHighlightingPass(file.project, editor.document, false) {

    private val highlights = ArrayList<HighlightInfo>()

    override fun doCollectInformation(progress: ProgressIndicator) {
        val manager = InjectedLanguageManager.getInstance(file.project)
        val files = (file.firstChild as? PsiInjectionText)?.let { manager.getInjectedPsiFiles(it) } ?: return
        for (pair in files) {
            val visitor = object : PsiElementVisitor() {
                private fun toHostTextRange(range: TextRange) = manager.injectedToHost(pair.first, range)

                private fun addHighlightInfo(range: TextRange, colors: ArendHighlightingColors) {
                    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(toHostTextRange(range)).textAttributes(colors.textAttributesKey).create()
                    if (info != null) {
                        highlights.add(info)
                    }
                }

                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    when (element) {
                        is ArendIPName -> addHighlightInfo(element.textRange, ArendHighlightingColors.OPERATORS)
                        is ArendLongName -> {
                            val dot = (element.parent as? ArendLiteral)?.dot
                            val last = if (dot != null) dot else {
                                val refs = element.refIdentifierList
                                val ref = refs.lastOrNull()
                                val resolved = ref?.reference?.let { runReadAction { it.resolve() } } as? ReferableBase<*>
                                if (resolved != null) {
                                    val alias = resolved.alias
                                    if (ReferableBase.calcPrecedence(alias?.prec).isInfix || alias == null && ReferableBase.calcPrecedence(resolved.prec).isInfix) {
                                        addHighlightInfo(ref.textRange, ArendHighlightingColors.OPERATORS)
                                    }
                                }
                                if (refs.size > 1) ref?.prevSibling else null
                            }
                            if (last != null) {
                                addHighlightInfo(TextRange(element.startOffset, last.endOffset), ArendHighlightingColors.LONG_NAME)
                            }
                        }
                    }
                }
            }
            PsiTreeUtil.processElements(pair.first) { it.accept(visitor); true }
        }
        for (range in file.errorRanges) {
            val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WEAK_WARNING).range(range).create()
            if (info != null) {
                highlights.add(info)
            }
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