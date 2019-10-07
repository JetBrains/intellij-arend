package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.module.ModulePath
import org.arend.psi.*
import org.arend.refactoring.addStatCmd
import org.arend.refactoring.findPlaceForNsCmd

class MisplacedImportQuickFix(private val misplacedStatCmdRef: SmartPsiElementPointer<ArendStatCmd>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.import"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = misplacedStatCmdRef.element != null

    override fun getText(): String = "Fix misplaced import"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val misplacedStatCmd = misplacedStatCmdRef.element
        val parent = misplacedStatCmd?.parent
        val parentCopy = parent?.copy()
        val containingFile = misplacedStatCmd?.containingFile

        if (misplacedStatCmd != null && parentCopy is ArendStatement && containingFile is ArendFile) {
            val path = ModulePath(misplacedStatCmd.path)
            val factory = ArendPsiFactory(project)

            parent.deleteWithNotification()
            addStatCmd(factory, parentCopy, findPlaceForNsCmd(containingFile, path))
        }
    }
}