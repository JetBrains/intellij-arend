package org.arend.refactoring

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import org.arend.psi.ext.PsiReferable

class ArendRenameInputValidator : RenameInputValidator {
    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(PsiReferable::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext) =
        ArendNamesValidator.INSTANCE.isIdentifier(newName, null)
}
