package org.vclang.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.moduleTextRepresentationImpl
import org.vclang.psi.ext.positionTextRepresentationImpl
import org.vclang.typechecking.TypeCheckingService


open class DataLocatedReferable(
    private var psiElementPointer: SmartPsiElementPointer<PsiElement>,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?)
    : DataLocatedReferableImpl(referable.precedence, referable.textRepresentation(), parent, typeClassReference, referable.kind), DataContainer, SourceInfo {

    override fun getData(): SmartPsiElementPointer<PsiElement> = psiElementPointer

    override fun moduleTextRepresentation(): String? = runReadAction { psiElementPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { psiElementPointer.element?.positionTextRepresentationImpl() }

    fun fixPointer(project: Project) = runReadAction {
        val psiRef = LocatedReferable.Helper.resolveReferable(this, TypeCheckingService.getInstance(project).libraryManager.moduleScopeProvider) as? PsiLocatedReferable
        if (psiRef != null) {
            psiElementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiRef)
        }
        psiRef
    }
}

class FieldDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>,
    referable: FieldReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?,
    private val underlyingField: TCReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, typeClassReference), TCFieldReferable {

    private val isExplicit = referable.isExplicitField

    override fun isExplicitField() = isExplicit

    override fun getUnderlyingReference() = underlyingField

    override fun getUnresolvedUnderlyingReference(): Reference? = null
}

class ClassDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    val superClassReferences: MutableList<TCClassReferable>,
    val fieldReferables: MutableList<TCFieldReferable>,
    val implementedFields: MutableList<TCReferable>,
    var underlyingClass: TCClassReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, null), TCClassReferable {
    var filledIn = false

    override fun getSuperClassReferences(): Collection<TCClassReferable> = superClassReferences

    override fun getFieldReferables(): Collection<TCFieldReferable> = fieldReferables

    override fun getImplementedFields(): Collection<TCReferable> = implementedFields

    override fun getUnderlyingReference() = underlyingClass

    override fun getUnresolvedUnderlyingReference(): Reference? = null

    override fun getUnresolvedSuperClassReferences(): List<Reference> = emptyList()
}
