package com.jetbrains.arend.ide.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.arend.ide.psi.ext.ArdCompositeElement
import com.jetbrains.arend.ide.psi.ext.PsiReferable

fun getPresentation(psi: ArdCompositeElement): ItemPresentation {
    val location = run {
        val module = psi.containingFile
        "(in ${(module as? ArdFile)?.fullName ?: module.name})"
    }

    val name = presentableName(psi)
    return PresentationData(name, location, psi.getIcon(0), null)
}

fun getPresentationForStructure(psi: ArdCompositeElement): ItemPresentation =
        PresentationData(presentableName(psi), null, psi.getIcon(0), null)

private fun presentableName(psi: PsiElement): String? = when (psi) {
    is ArdFile -> psi.fullName
    is PsiReferable -> psi.name
    else -> null
}
