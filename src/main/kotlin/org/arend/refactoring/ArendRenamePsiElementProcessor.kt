package org.arend.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageViewUtil
import org.arend.psi.ArendFile
import org.arend.util.FileUtils

class ArendRenamePsiElementProcessor: RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean {
        return element is ArendFile
    }

    override fun createRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement?, editor: Editor?): RenameDialog {
        return ArendRenameDialog(project, element, nameSuggestionContext, editor)
    }

    class ArendRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement?, editor: Editor?): RenameDialog(project, element, nameSuggestionContext, editor) {
        override fun getSuggestedNames(): Array<String> {
            return arrayOf(UsageViewUtil.getShortName(psiElement).substringBefore("."))
        }

        override fun getNewName(): String = patchedGetNewName() + FileUtils.EXTENSION

        private fun patchedGetNewName(): String = nameSuggestionsField.enteredName.trim()

        override fun canRun() {
            try {
                super.canRun()
            } catch (e: ConfigurationException) {
                throw ConfigurationException("'" + patchedGetNewName() + "'" + "is not a valid Arend module name")
            }
        }

        override fun areButtonsValid(): Boolean {
            return FileUtils.isModuleName(patchedGetNewName())
        }
    }
}