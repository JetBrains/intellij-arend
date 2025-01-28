package org.arend.quickfix.instance

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import org.arend.ext.reference.DataContainer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendLongName
import org.arend.typechecking.error.local.inference.RecursiveInstanceInferenceError
import org.arend.util.ArendBundle

class AddRecursiveInstanceArgumentQuickFix(private val error: RecursiveInstanceInferenceError, private val cause: SmartPsiElementPointer<ArendLongName>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.instance.addLocalRecursiveInstance")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null &&
            ((error.definition as? DataContainer?)?.data as? PsiElement)?.originalElement is ArendDefClass

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val defClass = ((error.definition as? DataContainer)?.data as? PsiElement)?.originalElement as? ArendDefClass ?: return

        val classRefName = error.classRef.refName
        val scope = defClass.scope.elements.map { it.textRepresentation() }
        val suggestedFieldName = classRefName.lowercase()
        val fieldName = if (scope.contains(suggestedFieldName)) {
            var counter = 0
            while (scope.contains(suggestedFieldName + counter.toString())) {
                counter++
            }
            suggestedFieldName + counter.toString()
        } else {
            suggestedFieldName
        }

        val psiFactory = ArendPsiFactory(project)
        val fieldTele = psiFactory.createFieldTele(fieldName, classRefName, false)

        defClass.defIdentifier?.addSiblingAfter(fieldTele)
        defClass.defIdentifier?.addSiblingAfter(psiFactory.createWhitespace(" "))
    }
}
