package org.vclang.resolving

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.ReferableConverter


class VcReferableConverter(private val file: PsiFile) : ReferableConverter {
    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) DataLocalReferable(SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer(referable, file), referable.textRepresentation())
        else referable

    override fun toDataLocatedReferable(referable: LocatedReferable?, isTypecheckable: Boolean): LocatedReferable? =
        // TODO[referable]
        /* if (referable is PsiElement) DataLocatedReferable(SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer(referable, file), referable.precedence, referable.textRepresentation(), toDataLocatedReferable(referable.locatedReferableParent, true), isTypecheckable)
        else */ referable
}