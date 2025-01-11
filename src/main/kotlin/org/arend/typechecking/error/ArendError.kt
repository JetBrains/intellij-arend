package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.GeneralError
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiConcreteReferable


// TODO[server2]: Delete this
class ArendError(val error: GeneralError, private val pointer: SmartPsiElementPointer<*>) : Comparable<ArendError> {
    private val definitionPointer = runReadAction {
        cause?.ancestor<PsiConcreteReferable>()?.let { SmartPointerManager.createPointer(it) }
    }

    val cause: PsiElement?
        get() = pointer.element

    val definition: PsiConcreteReferable?
        get() = definitionPointer?.element

    val file: ArendFile?
        get() = (definitionPointer ?: pointer).element?.containingFile as? ArendFile

    override fun compareTo(other: ArendError) = error.level.compareTo(other.error.level)

    override fun equals(other: Any?) = this === other || error == (other as? ArendError)?.error

    override fun hashCode() = error.hashCode()
}
