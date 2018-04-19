package org.vclang.resolving

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.naming.reference.*


open class DataLocatedReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, referable: LocatedReferable, parent: TCReferable?) : LocatedReferableImpl(referable.precedence, referable.textRepresentation(), parent, referable.isTypecheckable), DataContainer {
    override fun getData(): PsiElement? = psiElementPointer.element
}

class ClassDataLocatedReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, referable: LocatedReferable, parent: TCReferable?, private val superClassReferences: Collection<TCClassReferable>, private val fieldReferables: Collection<TCReferable>) : DataLocatedReferable(psiElementPointer, referable, parent), TCClassReferable {
    override fun getData(): PsiElement? = psiElementPointer.element

    override fun getSuperClassReferences(): Collection<TCClassReferable> = superClassReferences

    override fun getFieldReferables(): Collection<TCReferable> = fieldReferables
}
