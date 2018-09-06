package com.jetbrains.arend.ide.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.arend.ide.psi.ext.moduleTextRepresentationImpl
import com.jetbrains.arend.ide.psi.ext.positionTextRepresentationImpl
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer


class PsiSourceInfo(private val psiPointer: SmartPsiElementPointer<out PsiElement>) : SourceInfo, DataContainer {
    override fun getData() = psiPointer

    override fun moduleTextRepresentation() = runReadAction { psiPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation() = runReadAction { psiPointer.element?.positionTextRepresentationImpl() }
}