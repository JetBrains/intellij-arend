package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Variable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.ArendNsId
import org.arend.psi.ArendNsUsing
import org.arend.psi.ArendStatCmd
import org.arend.refactoring.AddIdToUsingAction
import org.arend.refactoring.RemoveRefFromStatCmdAction
import org.arend.refactoring.VariableImpl

class RenameDuplicateNameQuickFix(private val causeRef: SmartPsiElementPointer<PsiElement>,
                                  private val referable: Referable?) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.import"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causeRef.element != null

    override fun getText(): String = "Rename import"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        when (val cause = causeRef.element) {
            is ArendNsId -> {
                val using = cause.parent
                val statCmd = using?.parent
                if (using is ArendNsUsing && statCmd is ArendStatCmd) {
                    val oldName = cause.oldReference.textRepresentation()
                    val newName = cause.name
                    RemoveRefFromStatCmdAction(statCmd, cause.refIdentifier, false).execute(editor)
                    doRenameDuplicateName(editor, statCmd, oldName, newName)
                }
            }
            is ArendStatCmd -> {
                if (referable != null)
                    doRenameDuplicateName(editor, cause, referable.textRepresentation(), null)
            }
        }
    }

    companion object {
        fun doRenameDuplicateName(editor: Editor?, statCmd: ArendStatCmd, oldName: String, newName: String?) {
            val referables = statCmd.scope.elements.map { VariableImpl(it.textRepresentation()) }
            val variable = object : Variable { override fun getName(): String = newName ?: oldName }
            val freshName = StringRenamer().generateFreshName(variable, referables)
            val renamings = ArrayList<Pair<String, String?>>(); renamings.add(Pair(oldName, freshName))
            AddIdToUsingAction(statCmd, renamings).execute(editor)
        }


    }
}