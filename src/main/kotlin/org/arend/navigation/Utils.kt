package org.arend.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiReferable

fun getPresentation(psi: ArendCompositeElement): ItemPresentation {
    val location = run {
        val module = psi.containingFile
        "(in ${(module as? ArendFile)?.fullName ?: module.name})"
    }

    val name = presentableName(psi)
    return PresentationData(name, location, psi.getIcon(0), null)
}

fun getPresentationForStructure(psi: ArendCompositeElement): ItemPresentation =
        PresentationData(presentableName(psi), null, psi.getIcon(0), null)

private fun presentableName(psi: PsiElement): String? = when (psi) {
    is ArendFile -> psi.fullName
    is PsiReferable -> psi.name
    else -> null
}
