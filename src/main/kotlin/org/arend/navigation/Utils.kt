package org.arend.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiReferable
import javax.swing.Icon

fun getPresentation(psi: ArendCompositeElement): ItemPresentation {
    val location = run {
        val module = psi.containingFile
        "(in ${(module as? ArendFile)?.fullName ?: module.name})"
    }

    val name = presentableName(psi)
    var icon: Icon? = null
    ApplicationManager.getApplication().run {
        executeOnPooledThread {
            runReadAction {
                icon = psi.getIcon(0)
            }
        }.get()
    }

    return PresentationData(name, location, icon, null)
}

fun getPresentationForStructure(psi: ArendCompositeElement): ItemPresentation =
        PresentationData(presentableName(psi), null, psi.getIcon(0), null)

private fun presentableName(psi: PsiElement): String? = when (psi) {
    is ArendFile -> psi.fullName
    is PsiReferable -> psi.name
    else -> null
}
