package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.SourceInfo
import org.arend.naming.reference.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl
import org.arend.typechecking.TypeCheckingService


open class DataLocatedReferable(
    private var psiElementPointer: SmartPsiElementPointer<PsiElement>?,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?)
    : DataLocatedReferableImpl(referable.precedence, referable.textRepresentation(), parent, typeClassReference, referable.kind), DataContainer, SourceInfo {

    override fun getData() = psiElementPointer

    override fun moduleTextRepresentation(): String? = psiElementPointer?.let { runReadAction { it.element?.moduleTextRepresentationImpl() } }

    override fun positionTextRepresentation(): String? = psiElementPointer?.let { runReadAction { it.element?.positionTextRepresentationImpl() } }

    fun fixPointer(project: Project) =
        runReadAction {
            val psiRef = LocatedReferable.Helper.resolveReferable(this, TypeCheckingService.getInstance(project).libraryManager.moduleScopeProvider) as? PsiLocatedReferable
            if (psiElementPointer != null && psiRef != null) {
                psiElementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiRef)
            }
            psiRef
        }
}

class FieldDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>?,
    referable: FieldReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?,
    private val underlyingField: TCReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, typeClassReference), TCFieldReferable {

    private val isExplicit = referable.isExplicitField

    private val isParameter = referable.isParameterField

    override fun isExplicitField() = isExplicit

    override fun isParameterField() = isParameter

    override fun getUnderlyingReference() = underlyingField

    override fun getUnresolvedUnderlyingReference(): Reference? = null
}

class ClassDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>?,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    val superClasses: MutableList<TCClassReferable>,
    val fieldReferables: MutableList<TCFieldReferable>,
    val implementedFields: MutableList<TCReferable>,
    var underlyingClass: TCClassReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, null), TCClassReferable {
    var filledIn = false

    override fun getSuperClassReferences(): List<TCClassReferable> = superClasses

    override fun getFieldReferables(): Collection<TCFieldReferable> = fieldReferables

    override fun getImplementedFields(): Collection<TCReferable> = implementedFields

    override fun getUnderlyingReference() = underlyingClass

    override fun getUnresolvedUnderlyingReference(): Reference? = null

    override fun getUnresolvedSuperClassReferences(): List<Reference> = emptyList()
}
