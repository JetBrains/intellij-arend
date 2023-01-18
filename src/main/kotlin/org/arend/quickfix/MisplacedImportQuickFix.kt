package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.module.ModulePath
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.refactoring.addStatCmd
import org.arend.refactoring.findPlaceForNsCmd
import org.arend.util.ArendBundle

class MisplacedImportQuickFix(private val misplacedStatCmdRef: SmartPsiElementPointer<ArendStatCmd>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = misplacedStatCmdRef.element != null

    override fun getText(): String = ArendBundle.message("arend.import.fixMisplaced")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val misplacedStatCmd = misplacedStatCmdRef.element
        val parent = misplacedStatCmd?.parent
        val parentCopy = parent?.copy()
        val containingFile = misplacedStatCmd?.containingFile

        if (misplacedStatCmd != null && parentCopy is ArendStat && containingFile is ArendFile) {
            val path = ModulePath(misplacedStatCmd.path)
            val factory = ArendPsiFactory(project)

            parent.delete()
            addStatCmd(factory, parentCopy, findPlaceForNsCmd(containingFile, path))
        }
    }
}