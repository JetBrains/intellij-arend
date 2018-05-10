package org.vclang.typechecking.error

import com.intellij.psi.PsiErrorElement
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable


class ParserError(private val psiElement: PsiErrorElement, private val referable: GlobalReferable) : GeneralError(Level.ERROR, psiElement.errorDescription) {
    override fun getAffectedDefinitions(): List<GlobalReferable> = listOf(referable)

    override fun getCause() = PsiSourceInfo(psiElement)
}