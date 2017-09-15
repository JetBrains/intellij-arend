package org.vclang.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import org.vclang.psi.VcFile
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.PsiReferable

fun getPresentation(psi: VcCompositeElement): ItemPresentation {
    val location = run {
        val module = psi.containingFile as? VcFile
        "(in ${module?.relativeModulePath ?: psi.containingFile.name})"
    }

    val name = presentableName(psi)
    return PresentationData(name, location, psi.getIcon(0), null)
}

fun getPresentationForStructure(psi: VcCompositeElement): ItemPresentation =
        PresentationData(presentableName(psi), null, psi.getIcon(0), null)

private fun presentableName(psi: PsiElement): String? = when (psi) {
    is VcFile -> psi.relativeModulePath.toString()
    is PsiReferable -> psi.name
    else -> null
}
