package org.arend.typechecking.error

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.GeneralError
import org.arend.ext.reference.ArendRef
import org.arend.naming.reference.GlobalReferable
import java.util.function.BiConsumer


class ParserError(private val psiElement: SmartPsiElementPointer<PsiErrorElement>, private val referable: GlobalReferable, errorDescription: String)
    : GeneralError(Level.ERROR, errorDescription) {

    override fun forAffectedDefinitions(consumer: BiConsumer<ArendRef, GeneralError>) {
        consumer.accept(referable, this)
    }

    override fun getCause() = PsiSourceInfo(psiElement)

    override fun getStage() = Stage.PARSER
}