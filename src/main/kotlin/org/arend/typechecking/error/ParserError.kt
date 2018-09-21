package org.arend.typechecking.error

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.GeneralError
import org.arend.naming.reference.GlobalReferable


class ParserError(private val psiElement: SmartPsiElementPointer<PsiErrorElement>, private val referable: GlobalReferable, errorDescription: String) : GeneralError(Level.ERROR, errorDescription) {
    override fun getAffectedDefinitions(): List<GlobalReferable> = listOf(referable)

    override fun getCause() = PsiSourceInfo(psiElement)
}