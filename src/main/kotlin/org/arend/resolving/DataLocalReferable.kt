package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.arend.error.SourceInfo
import org.arend.naming.reference.DataContainer
import org.arend.naming.reference.LocalReferable
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl


class DataLocalReferable(private val psiElementPointer: SmartPsiElementPointer<PsiElement>, name: String) : LocalReferable(name), DataContainer, SourceInfo {
    override fun getData(): SmartPsiElementPointer<PsiElement> = psiElementPointer

    override fun moduleTextRepresentation(): String? = runReadAction { psiElementPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { psiElementPointer.element?.positionTextRepresentationImpl() }
}