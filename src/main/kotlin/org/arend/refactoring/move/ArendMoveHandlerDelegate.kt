package org.arend.refactoring.move

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.arend.psi.*
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.term.abs.Abstract

class ArendMoveHandlerDelegate: MoveHandlerDelegate() {

    override fun tryToMove(element: PsiElement?, project: Project?, dataContext: DataContext?, reference: PsiReference?, editor: Editor?): Boolean {
        if (project != null &&
                element is ArendDefinition && element is Abstract.Definition && element.enclosingClass == null) {
            val elements: Array<ArendDefinition> = arrayOf(element)

            if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, elements.toList(), true)) return false

            return showDialog(project, elements.toList())
        }

        return false
    }

    private fun showDialog(project: Project, elements: List<ArendDefinition>): Boolean {
        if (elements.isNotEmpty()) {
            val group = elements.first().parentGroup
            if (group != null && elements.subList(1, elements.size).map { it.parentGroup }.all { it == group } && // Ensure elements are members of the same group
                    group is ArendFile || group is DefinitionAdapter<*>) {
                ArendMoveMembersDialog(project, elements, group).show()
                return true
            }
        }
        return false
    }

}