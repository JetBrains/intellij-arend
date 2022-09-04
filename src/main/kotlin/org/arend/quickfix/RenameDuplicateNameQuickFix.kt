package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.ext.ArendNsId
import org.arend.psi.ext.ArendNsUsing
import org.arend.psi.ext.ArendStatCmd
import org.arend.refactoring.doAddIdToUsing
import org.arend.refactoring.doRemoveRefFromStatCmd
import org.arend.util.ArendBundle

class RenameDuplicateNameQuickFix(private val causeRef: SmartPsiElementPointer<PsiElement>,
                                  private val referable: Referable?) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causeRef.element != null

    override fun getText(): String = ArendBundle.message("arend.import.rename")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        when (val cause = causeRef.element) {
            is ArendNsId -> {
                val using = cause.parent
                val statCmd = using?.parent
                if (using is ArendNsUsing && statCmd is ArendStatCmd) {
                    val oldName = cause.oldReference.textRepresentation()
                    val newName = cause.name
                    doRemoveRefFromStatCmd(cause.refIdentifier, false)
                    doRenameDuplicateName(statCmd, oldName, newName)
                }
            }
            is ArendStatCmd -> {
                if (referable != null)
                    doRenameDuplicateName(cause, referable.textRepresentation(), null)
            }
        }
    }

    companion object {
        fun doRenameDuplicateName(statCmd: ArendStatCmd, oldName: String, newName: String?) {
            val referables = statCmd.scope.getElements(null).map { VariableImpl(it.textRepresentation()) }
            val variable = Variable { newName ?: oldName }
            val freshName = StringRenamer().generateFreshName(variable, referables)
            val renamings = ArrayList<Pair<String, String?>>(); renamings.add(Pair(oldName, freshName))
            doAddIdToUsing(statCmd, renamings)
        }


    }
}