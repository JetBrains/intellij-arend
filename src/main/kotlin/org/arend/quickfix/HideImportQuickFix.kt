package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendNsId
import org.arend.psi.ext.ArendNsUsing
import org.arend.psi.ext.ArendStatCmd
import org.arend.refactoring.doAddIdToHiding
import org.arend.refactoring.doRemoveRefFromStatCmd
import org.arend.util.ArendBundle
import java.util.Collections.singletonList

class HideImportQuickFix(private val causeRef: SmartPsiElementPointer<PsiElement>,
                         private val referable: Referable?): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.import.hide")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        when (val cause = causeRef.element) {
            is ArendNsId -> {
                val using = cause.parent
                val statCmd = using?.parent
                if (using is ArendNsUsing && statCmd is ArendStatCmd)
                    doRemoveRefFromStatCmd(cause.refIdentifier, false)
            }
            is ArendStatCmd -> {
                if (referable != null) doAddIdToHiding(cause, singletonList(referable.textRepresentation()))
            }
        }
    }
}