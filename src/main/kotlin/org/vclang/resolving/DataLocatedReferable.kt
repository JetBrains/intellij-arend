package org.vclang.resolving

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.naming.reference.*
import java.lang.ref.WeakReference


open class DataLocatedReferable(psiElementPointer: SmartPsiElementPointer<PsiElement>, referable: LocatedReferable, parent: LocatedReferable?) : LocatedReferableImpl(referable.precedence, referable.textRepresentation(), parent, referable.isTypecheckable), DataContainer {
    private val weakPsiElementPointer = WeakReference(psiElementPointer)

    override fun getData(): PsiElement? = weakPsiElementPointer.get()?.element
}

class ClassDataLocatedReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, referable: LocatedReferable, parent: LocatedReferable?, private val superClassReferences: Collection<TCClassReferable>, private val fieldReferables: Collection<TCReferable>) : DataLocatedReferable(psiElementPointer, referable, parent), TCClassReferable {
    override fun getData(): PsiElement? = psiElementPointer.element

    override fun getSuperClassReferences(): Collection<TCClassReferable> = superClassReferences

    override fun getFieldReferables(): Collection<TCReferable> = fieldReferables
}
