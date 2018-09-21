package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.SourceInfo
import org.arend.naming.reference.DataContainer
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl


class PsiSourceInfo(private val psiPointer: SmartPsiElementPointer<out PsiElement>) : SourceInfo, DataContainer {
    override fun getData() = psiPointer

    override fun moduleTextRepresentation() = runReadAction { psiPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation() = runReadAction { psiPointer.element?.positionTextRepresentationImpl() }
}