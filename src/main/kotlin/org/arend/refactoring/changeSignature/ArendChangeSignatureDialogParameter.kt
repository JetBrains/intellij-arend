package org.arend.refactoring.changeSignature

import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendExpr
import org.arend.resolving.util.ReferableExtractVisitor

class ArendChangeSignatureDialogParameter(val item: ArendChangeSignatureDialogParameterTableModelItem): TypedReferable {
    override fun textRepresentation(): String = item.resultParameterInfo.name

    override fun getTypeClassReference(): ClassReferable? {
        val type = item.typeCodeFragment.childOfType<ArendExpr>()
        return if (type != null) ReferableExtractVisitor().findClassReferable(type) else null
    }
}