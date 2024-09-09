package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.naming.reference.DataLocalReferable
import org.arend.psi.ancestor
import org.arend.psi.descendantOfType
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.performTextModification
import org.arend.typechecking.error.local.ElimSubstError
import org.arend.util.ArendBundle

class ElimSubstQuickFix(
    private val cause: SmartPsiElementPointer<PsiElement>,
    private val error: ElimSubstError
) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.elim.substitute")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val caseExpression = cause.element?.ancestor<ArendCaseExpr>() ?: return
        val caseArgs = caseExpression.caseArguments
        val existingCaseArgs = LinkedHashMap<ArendDefIdentifier, ArendCaseArg>()
        val notEliminatedBindings = error.notEliminatedBindings.toList()
        val notEliminatedDefIdentifiers = notEliminatedBindings.map { (it as? DataLocalReferable)?.data }.toList()
        val bindingsToCreate = HashSet<ArendDefIdentifier>()
        bindingsToCreate.addAll(notEliminatedDefIdentifiers.filterIsInstance<ArendDefIdentifier>())

        val eliminationSequence = ArrayList<ArendDefIdentifier>()
        val nonElimCaseArgs = ArrayList<ArendCaseArg>()
        for (x in caseArgs) {
            val dI = x.descendantOfType<ArendRefIdentifier>()?.resolve
            if (dI !is ArendDefIdentifier) {
                nonElimCaseArgs.add(x)
            } else {
                eliminationSequence.add(dI)
                existingCaseArgs[dI] = x
            }
        }
        for (b in bindingsToCreate) if (!eliminationSequence.contains(b)) {
            eliminationSequence.add(b)
        }
        val sortedEliminationSequence = eliminationSequence.sortedBy { it.startOffset }
        val clauseList = caseExpression.withBody?.clauseList ?: emptyList()
        val clausesPatternSequences = clauseList.map { "" }.toTypedArray()
        var elimSequence = ""

        fun addItem(cA: ArendCaseArg, text: String = cA.text) {
            val index = caseArgs.indexOf(cA)
            if (elimSequence != "") elimSequence += ", "

            val dI = cA.descendantOfType<ArendRefIdentifier>()?.resolve
            elimSequence += if (dI != null && bindingsToCreate.contains(dI)) "\\elim ${dI.text}" else text

            for ((i, clause) in clauseList.withIndex()) {
                val patternText = clause.patterns.getOrNull(index)?.text ?: "_"
                var sequence = clausesPatternSequences[i]
                if (sequence != "") sequence += ", "
                sequence += patternText
                clausesPatternSequences[i] = sequence
            }
        }
        fun addHole() {
            for ((i, _) in clauseList.withIndex()) {
                var sequence = clausesPatternSequences[i]
                if (sequence != "") sequence += ", "
                sequence += "_"
                clausesPatternSequences[i] = sequence
            }
        }

        for (d in sortedEliminationSequence) {
            val existing = existingCaseArgs[d]
            if (existing != null) {
                addItem(existing)
            } else {
                if (elimSequence != "") elimSequence += ", "
                elimSequence += "\\elim ${d.text}"
                addHole()
            }
        }

        for (nCA in nonElimCaseArgs) addItem(nCA)


        performTextModification(caseExpression, elimSequence, caseArgs.first().startOffset, caseArgs.last().endOffset)

        for (clause in clauseList.withIndex()) {
            performTextModification(clause.value, clausesPatternSequences[clause.index], clause.value.patterns.first().startOffset, clause.value.patterns.last().endOffset)
        }
    }

}
