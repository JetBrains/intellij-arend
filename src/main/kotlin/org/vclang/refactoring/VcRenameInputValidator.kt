package org.vclang.refactoring

import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidator
import com.intellij.util.ProcessingContext
import org.vclang.psi.ext.PsiReferable

/**
 * Created by Sinchuk Sergey on 12/6/17.
 */
class VcRenameInputValidator : RenameInputValidator {
    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(PsiReferable::class.java)

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext) =
        VcNamesValidator.isPrefixName(newName)
}
