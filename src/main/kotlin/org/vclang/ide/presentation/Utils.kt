package org.vclang.ide.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcNamedElement

fun getPresentationForStructure(psi: VcCompositeElement): ItemPresentation =
        PresentationData(presentableName(psi), null, psi.getIcon(0), null)

private fun presentableName(psi: PsiElement): String? = when (psi) {
    is VcFile -> psi.modulePath.toString()
    is VcNamedElement -> psi.name
    else -> null
}
