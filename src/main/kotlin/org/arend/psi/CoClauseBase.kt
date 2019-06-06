package org.arend.psi

import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.term.abs.Abstract
import org.arend.typing.ReferableExtractVisitor


interface CoClauseBase : ClassReferenceHolder, Abstract.ClassFieldImpl, ArendCompositeElement {
    fun getCoClauseList(): List<ArendCoClause>

    fun getLbrace(): PsiElement?

    fun getLongName(): ArendLongName?

    fun getResolvedImplementedField(): Referable?

    companion object {
        fun getClassReference(coClauseBase: CoClauseBase): ClassReferable? {
            val resolved = coClauseBase.getResolvedImplementedField()
            return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
        }

        fun getClassReferenceData(coClauseBase: CoClauseBase): ClassReferenceData? {
            val resolved = coClauseBase.getResolvedImplementedField()
            if (resolved is ClassReferable) {
                return ClassReferenceData(resolved, emptyList(), emptySet(), false)
            }

            val visitor = ReferableExtractVisitor(true)
            val classRef = visitor.findReferable((resolved as? TypedReferable)?.typeOf) as? ClassReferable ?: return null
            return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields, true)
        }
    }
}