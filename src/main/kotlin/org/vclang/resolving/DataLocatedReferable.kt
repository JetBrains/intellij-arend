package org.vclang.resolving

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferableImpl
import com.jetbrains.jetpad.vclang.term.Precedence


class DataLocatedReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, precedence: Precedence, name: String, parent: LocatedReferable?, isTypecheckable: Boolean) : LocatedReferableImpl(precedence, name, parent, isTypecheckable), DataContainer {
    override fun getData(): PsiElement? = psiElementPointer.element
}