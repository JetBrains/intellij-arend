package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.GeneralError
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement


class ArendError(val error: GeneralError, private val pointer: SmartPsiElementPointer<*>) : Comparable<ArendError> {
    private val definitionPointer = runReadAction {
        cause?.ancestor<ArendDefinition>()?.let { SmartPointerManager.createPointer(it) }
    }

    val cause: ArendCompositeElement?
        get() = pointer.element as? ArendCompositeElement

    val definition: ArendDefinition?
        get() = definitionPointer?.element

    val inDefinition: Boolean
        get() = definitionPointer != null

    val file: ArendFile?
        get() = ((definitionPointer ?: pointer).element as? ArendCompositeElement)?.containingFile as? ArendFile

    override fun compareTo(other: ArendError) = error.level.compareTo(other.error.level)

    override fun equals(other: Any?) = this === other || error == (other as? ArendError)?.error

    override fun hashCode() = error.hashCode()
}
