package org.arend.injection

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.highlight.ArendHighlightingColors
import org.arend.highlight.HighlightingCollector
import java.util.ArrayList

class InjectionHighlightingPass(val file: PsiInjectionTextFile, private val editor: Editor)
    : TextEditorHighlightingPass(file.project, editor.document, false) {

    private val highlights = ArrayList<HighlightInfo>()

    override fun doCollectInformation(progress: ProgressIndicator) {
        val manager = InjectedLanguageManager.getInstance(file.project)
        val files = (file.firstChild as? PsiInjectionText)?.let { manager.getInjectedPsiFiles(it) } ?: return
        for (pair in files) {
            val collector = object : HighlightingCollector {
                private fun toHostTextRange(range: TextRange) = manager.injectedToHost(pair.first, range)

                override fun addHighlightInfo(range: TextRange, colors: ArendHighlightingColors) {
                    val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(toHostTextRange(range)).textAttributes(colors.textAttributesKey).create()
                    if (info != null) {
                        highlights.add(info)
                    }
                }
            }


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