package org.arend.typechecking.error

import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.GeneralError
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement


data class ArendError(val error: GeneralError, val pointer: SmartPsiElementPointer<*>) : Comparable<ArendError> {
    val cause
        get() = pointer.element as? ArendCompositeElement

    val definition
        get() = pointer.element?.ancestor<ArendDefinition>()

    val file
        get() = pointer.element?.containingFile as? ArendFile

    override fun compareTo(other: ArendError) = error.level.compareTo(other.error.level)
}
