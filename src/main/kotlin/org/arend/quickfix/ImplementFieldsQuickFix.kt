package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendPsiFactory
import org.arend.psi.addAfterWithNotification
import org.arend.refactoring.moveCaretToEndOffset

class ImplementFieldsQuickFix(val instance: AbstractCoClauseInserter, private val needsBulb: Boolean, private val fieldsToImplement: List<Pair<LocatedReferable, Boolean>>) : IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = "Implement missing fields"

    private fun addField(field: Referable, editor: Editor?, psiFactory: ArendPsiFactory, needQualifiedName: Boolean = false) {
        val coClauses = instance.coClausesList
        val fieldClass = (field as? LocatedReferable)?.locatedReferableParent
        val name = if (needQualifiedName && fieldClass != null) "${fieldClass.textRepresentation()}.${field.textRepresentation()}" else field.textRepresentation()

        if (coClauses.isEmpty()) {
            instance.insertFirstCoClause(name, psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()
            val coClause = psiFactory.createCoClause(name)

            anchor.parent.addAfterWithNotification(coClause, anchor)
            if (!caretMoved && editor != null) {
                moveCaretToEndOffset(editor, anchor.nextSibling)
                caretMoved = true
            }
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"), anchor)
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        for (f in fieldsToImplement) addField(f.first, editor, psiFactory, f.second)
    }

    override fun getIcon(flags: Int) = if (needsBulb) AllIcons.Actions.IntentionBulb else null
}