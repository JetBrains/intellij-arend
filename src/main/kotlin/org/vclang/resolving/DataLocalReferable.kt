package org.vclang.resolving

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import com.jetbrains.jetpad.vclang.naming.reference.LocalReferable


class DataLocalReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, name: String) : LocalReferable(name), DataContainer {
    override fun getData(): PsiElement? = psiElementPointer.element
}