package org.arend.quickfix.implementCoClause

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.moveCaretToEndOffset
import org.arend.util.ArendBundle

open class ImplementFieldsQuickFix(private val instanceRef: SmartPsiElementPointer<PsiElement>,
                              private val needsBulb: Boolean,
                              private val fieldsToImplement: List<Pair<LocatedReferable, Boolean>>): IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = text

    override fun getText() = ArendBundle.message("arend.coClause.implementMissing")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        instanceRef.element != null

    private fun addField(field: Referable, inserter: AbstractCoClauseInserter, editor: Editor?, psiFactory: ArendPsiFactory, needQualifiedName: Boolean = false) {
        val coClauses = inserter.coClausesList
        val fieldClass = (field as? LocatedReferable)?.locatedReferableParent
        val name = if (needQualifiedName && fieldClass != null) "${fieldClass.textRepresentation()}.${field.textRepresentation()}" else field.textRepresentation()

        if (coClauses.isEmpty()) {
            inserter.insertFirstCoClause(name, psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()
            val coClause = when (anchor) {
                is ArendCoClause -> psiFactory.createCoClause(name)
                is ArendLocalCoClause -> psiFactory.createLocalCoClause(name)
                else -> null
            }

            if (coClause != null) {
                val pipeSample = coClause.findPrevSibling()
                val insertedCoClause = anchor.parent.addAfter(coClause, anchor)
                if (insertedCoClause is ArendCoClause && pipeSample != null) {
                    anchor.parent.addBefore(pipeSample, insertedCoClause)
                }
                if (!caretMoved && editor != null) {
                    moveCaretToEndOffset(editor, anchor.nextSibling)
                    caretMoved = true
                }
                anchor.parent.addAfter(psiFactory.createWhitespace("\n"), anchor)
            }
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val instance = instanceRef.element ?: return
        val psiFactory = ArendPsiFactory(project)
        val firstCCInserter = makeFirstCoClauseInserter(instance) ?: return
        for (f in fieldsToImplement) addField(f.first, firstCCInserter, editor, psiFactory, f.second)
    }

    override fun getIcon(flags: Int) = if (needsBulb) AllIcons.Actions.IntentionBulb else null
}