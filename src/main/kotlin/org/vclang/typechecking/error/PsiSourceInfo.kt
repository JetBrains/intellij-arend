package org.vclang.typechecking.error

import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import org.vclang.psi.ext.moduleTextRepresentationImpl
import org.vclang.psi.ext.positionTextRepresentationImpl


class PsiSourceInfo(private val psiElement: PsiElement) : SourceInfo, DataContainer {
    override fun getData() = psiElement

    override fun moduleTextRepresentation() = psiElement.moduleTextRepresentationImpl()

    override fun positionTextRepresentation() = psiElement.positionTextRepresentationImpl()
}