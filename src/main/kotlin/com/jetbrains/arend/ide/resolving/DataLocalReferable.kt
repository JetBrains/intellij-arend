package com.jetbrains.arend.ide.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.arend.ide.psi.ext.moduleTextRepresentationImpl
import com.jetbrains.arend.ide.psi.ext.positionTextRepresentationImpl
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import com.jetbrains.jetpad.vclang.naming.reference.LocalReferable


class DataLocalReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, name: String) : LocalReferable(name), DataContainer, SourceInfo {
    override fun getData(): SmartPsiElementPointer<PsiElement> = psiElementPointer

    override fun moduleTextRepresentation(): String? = runReadAction { psiElementPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { psiElementPointer.element?.positionTextRepresentationImpl() }
}