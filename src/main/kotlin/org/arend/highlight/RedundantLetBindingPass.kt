package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInspection.HintAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.suggested.startOffset
import org.arend.naming.reference.Referable
import org.arend.psi.ArendFile
import org.arend.psi.deleteWithWhitespaces
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendLetClause
import org.arend.psi.ext.ArendLetExpr
import org.arend.psi.ext.ArendPattern
import org.arend.psi.findPrevSibling
import org.arend.util.ArendBundle

class RedundantLetBindingPass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor):
    BasePass(file, editor, "Arend redundant let binding", TextRange(0, editor.document.textLength), highlightInfoProcessor) {

    private fun addDefIdentifiers(arendPattern: ArendPattern?, defIdentifiers: MutableList<ArendDefIdentifier>) {
        if (arendPattern == null) {
            return
        }
        arendPattern.singleReferable?.let { defIdentifiers.add(it) }
        for (subPatterns in arendPattern.sequence) {
            addDefIdentifiers(subPatterns, defIdentifiers)
        }
    }

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        file.descendantsOfType<ArendLetClause>().filter {
            val letExpr = it.parent as ArendLetExpr
            var hasElement = false

            val defIdentifiers = mutableListOf<ArendDefIdentifier>()
            it.referable?.let { defIdentifier -> defIdentifiers.add(defIdentifier) }
            addDefIdentifiers(it.pattern, defIdentifiers)

            letExpr.expr?.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element is Referable && defIdentifiers.contains(element.reference?.resolve())) {
                        hasElement = true
                    }
                    super.visitElement(element)
                }
            })
            !hasElement
        }.forEach {
            val builder = HighlightInfo
                .newHighlightInfo(HighlightInfoType.WARNING)
                .range(it.textRange)
                .severity(HighlightSeverity.WARNING)
                .descriptionAndTooltip(ArendBundle.message("arend.inspection.remove.letBinding.message"))
            registerFix(builder, RemoveLetBindingHintAction(it))
            addHighlightInfo(builder)
        }
    }

    companion object {
        class RemoveLetBindingHintAction(private val letClause: ArendLetClause) : HintAction {

            override fun startInWriteAction(): Boolean = true

            override fun getFamilyName(): String = text

            override fun getText(): String = ArendBundle.message("arend.inspection.remove.letBinding")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val letExpr = letClause.parent as ArendLetExpr
                if (letExpr.letClauses.size > 1) {
                    letClause.findPrevSibling { it.text == "|" }?.deleteWithWhitespaces()
                    letClause.deleteWithWhitespaces()
                } else {
                    letExpr.startOffset.let { startOffset -> letExpr.expr?.startOffset?.let { endOffset ->
                        editor?.document?.deleteString(startOffset, endOffset)
                    } }
                }
            }

            override fun showHint(editor: Editor): Boolean = true
        }
    }
}
