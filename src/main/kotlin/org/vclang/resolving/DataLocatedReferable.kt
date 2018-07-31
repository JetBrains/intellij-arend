package org.vclang.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.*
import org.vclang.psi.ext.moduleTextRepresentationImpl
import org.vclang.psi.ext.positionTextRepresentationImpl


open class DataLocatedReferable(
    private val psiElementPointer: SmartPsiElementPointer<PsiElement>,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?)
    : DataLocatedReferableImpl(referable.precedence, referable.textRepresentation(), parent, typeClassReference, referable.kind), DataContainer, SourceInfo {

    override fun getData(): SmartPsiElementPointer<PsiElement> = psiElementPointer

    override fun moduleTextRepresentation(): String? = runReadAction { psiElementPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { psiElementPointer.element?.positionTextRepresentationImpl() }
}

class FieldDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?,
    private val underlyingField: TCReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, typeClassReference) {

    override fun getUnderlyingReference() = underlyingField

    override fun getUnresolvedUnderlyingReference(): Reference? = null
}

class ClassDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    val superClassReferences: MutableList<TCClassReferable>,
    val fieldReferables: MutableList<TCReferable>,
    var underlyingClass: TCClassReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, null), TCClassReferable {

    var filledIn = false

    override fun getSuperClassReferences(): Collection<TCClassReferable> = superClassReferences

    override fun getFieldReferables(): Collection<TCReferable> = fieldReferables

    override fun getUnderlyingReference() = underlyingClass

    override fun getUnresolvedUnderlyingReference(): Reference? = null

    override fun getUnresolvedSuperClassReferences(): List<Reference> = emptyList()
}
