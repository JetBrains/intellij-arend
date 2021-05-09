package org.arend.psi

import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TypedReferable
import org.arend.naming.scope.LazyScope
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiReferable
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ReferableExtractVisitor


interface CoClauseBase : ClassReferenceHolder, Abstract.ClassFieldImpl, ArendCompositeElement {
    val localCoClauseList: List<ArendLocalCoClause>

    val lbrace: PsiElement?

    val longName: ArendLongName?

    val lamParamList: List<ArendLamParam>

    val resolvedImplementedField: Referable?

    val fatArrow: PsiElement?

    val expr: ArendExpr?

    companion object {
        fun getClassReference(coClauseBase: CoClauseBase): ClassReferable? {
            val resolved = coClauseBase.resolvedImplementedField
            return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
        }

        fun getClassReferenceData(coClauseBase: CoClauseBase): ClassReferenceData? {
            val resolved = coClauseBase.resolvedImplementedField
            if (resolved is ClassReferable) {
                return ClassReferenceData(resolved, emptyList(), emptySet(), false)
            }

            val psiRef = resolved as? PsiReferable ?: return null
            val visitor = ReferableExtractVisitor(true)
            val classRef = visitor.findClassReference(visitor.findReferable(psiRef.typeOf), LazyScope { (psiRef.psiElementType as? ArendCompositeElement)?.scope ?: coClauseBase.scope }) ?: return null
            return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields, true)
        }
    }
}