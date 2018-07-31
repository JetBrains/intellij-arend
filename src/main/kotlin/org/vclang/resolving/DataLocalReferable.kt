package org.vclang.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import com.jetbrains.jetpad.vclang.naming.reference.LocalReferable
import org.vclang.psi.ext.moduleTextRepresentationImpl
import org.vclang.psi.ext.positionTextRepresentationImpl


class DataLocalReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, name: String) : LocalReferable(name), DataContainer, SourceInfo {
    override fun getData(): SmartPsiElementPointer<PsiElement> = psiElementPointer

    override fun moduleTextRepresentation(): String? = runReadAction { psiElementPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { psiElementPointer.element?.positionTextRepresentationImpl() }
}