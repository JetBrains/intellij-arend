package org.vclang.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import org.vclang.psi.ext.moduleTextRepresentationImpl
import org.vclang.psi.ext.positionTextRepresentationImpl


class PsiSourceInfo(private val psiPointer: SmartPsiElementPointer<out PsiElement>) : SourceInfo, DataContainer {
    override fun getData() = psiPointer

    override fun moduleTextRepresentation() = runReadAction { psiPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation() = runReadAction { psiPointer.element?.positionTextRepresentationImpl() }
}