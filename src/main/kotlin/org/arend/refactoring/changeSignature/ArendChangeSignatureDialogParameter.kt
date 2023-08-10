package org.arend.refactoring.changeSignature

import com.intellij.psi.util.elementType
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.ArendElementTypes
import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendExpr
import org.arend.resolving.util.ReferableExtractVisitor

class ArendChangeSignatureDialogParameter(val item: ArendChangeSignatureDialogParameterTableModelItem): TypedReferable {
    override fun textRepresentation(): String = item.parameter.name

    override fun getTypeClassReference(): ClassReferable? {
        val type = item.typeCodeFragment.descendantOfType<ArendExpr>()
        return if (type != null && type.elementType != ArendElementTypes.EXPR) ReferableExtractVisitor().findClassReferable(type) else null
    }
}