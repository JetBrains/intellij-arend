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
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendNameTele
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.util.ArendBundle

class RedundantParameterNamePass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor):
    BasePass(file, editor, "Arend redundant parameter name annotator", TextRange(0, editor.document.textLength), highlightInfoProcessor) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val identifiers = file.descendantsOfType<ArendNameTele>().map {
            it.identifierOrUnknownList
                .filter { identifier -> identifier.defIdentifier != null }
                .map { identifier -> identifier.defIdentifier!! }
        }.flatten()
        val arendRefIdentifiers = file.descendantsOfType<ArendRefIdentifier>().map { it.resolve }.toList()

        for (identifier in identifiers) {
            if (!arendRefIdentifiers.contains(identifier)) {
                val builder = HighlightInfo
                    .newHighlightInfo(HighlightInfoType.WARNING)
                    .range(identifier.textRange)
                    .severity(HighlightSeverity.WARNING)
                    .descriptionAndTooltip(ArendBundle.message("arend.inspection.parameter.redundant", identifier.name))
                registerFix(builder, RedundantParameterNameHintAction(identifier))
                addHighlightInfo(builder)
            }
        }
    }

    companion object {
        class RedundantParameterNameHintAction(private val defIdentifier: ArendDefIdentifier): HintAction {

            override fun startInWriteAction(): Boolean = true

            override fun getFamilyName(): String = text

            override fun getText(): String = ArendBundle.message("arend.inspection.redundant.parameter.message")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val psiFactory = ArendPsiFactory(project)
                val underlining = psiFactory.createUnderlining()
                defIdentifier.replace(underlining)
            }

            override fun showHint(editor: Editor): Boolean = true
        }
    }
}
