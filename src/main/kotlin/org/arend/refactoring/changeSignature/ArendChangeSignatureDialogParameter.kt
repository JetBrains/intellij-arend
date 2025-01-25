package org.arend.refactoring.changeSignature

import org.arend.naming.reference.Referable

class ArendChangeSignatureDialogParameter(val item: ArendChangeSignatureDialogParameterTableModelItem): Referable {
    override fun textRepresentation(): String = item.parameter.name
}