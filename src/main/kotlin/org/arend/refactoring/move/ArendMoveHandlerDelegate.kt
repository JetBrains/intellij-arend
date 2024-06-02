package org.arend.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ext.ArendGroup
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.isMoveableGroup
import org.arend.util.ArendBundle
import java.util.Collections.singletonList

class ArendMoveHandlerDelegate: MoveHandlerDelegate() {

    override fun tryToMove(element: PsiElement?, project: Project?, dataContext: DataContext?, reference: PsiReference?, editor: Editor?): Boolean =
        if (reference?.resolve()?.let { checkMoveable(it) } == true)
            showDialog(reference.resolve() as ArendGroup)
        else if (element != null && checkMoveable(element))
            showDialog(element as ArendGroup)
        else
            false


    private fun showDialog(element: ArendGroup): Boolean {
        val group = element.parentGroup
        if (group is PsiElement) {
            val module = group.module
            val isWritable = CommonRefactoringUtil.checkReadOnlyStatus(element.project, singletonList(element), true)
            return if (module != null && isWritable) {
                ArendMoveMembersDialog(element.project, singletonList(element), group, module).show()
                true
            } else false
        }
        return false
    }

    override fun canMove(elements: Array<out PsiElement>?, targetContainer: PsiElement?, reference: PsiReference?): Boolean =
        reference?.resolve()?.let {
            checkMoveable(it)
        } == true ||
        elements?.firstOrNull()?.let {
            checkMoveable(it)
        } == true

    override fun getActionName(elements: Array<out PsiElement>): String {
        return ArendBundle.message("arend.refactoring.move.name", (elements.first() as? ArendGroup)?.refName ?: "???")
    }

    override fun supportsLanguage(language: Language): Boolean = language == ArendLanguage.INSTANCE

    companion object {
        fun getCommonContainer(elements: List<ArendGroup>): ArendGroup? {
            val group = elements.first().parentGroup
            return if (group != null && elements.subList(1, elements.size).map { it.parentGroup }.all { it == group }) group else null
        }

        fun checkMoveable(element: PsiElement) =
            element is ArendGroup && element !is ArendFile && isMoveableGroup(element)
    }

}