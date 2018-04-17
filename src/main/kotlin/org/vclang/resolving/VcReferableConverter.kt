package org.vclang.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter


class VcLocalReferableConverter(private val project: Project) : ReferableConverter {
    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) DataLocalReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.textRepresentation())
        else referable

    override fun toDataLocatedReferable(referable: LocatedReferable?): LocatedReferable? = referable
}

class VcReferableConverter(private val project: Project) : ReferableConverter {
    private val cache = HashMap<PsiElement, LocatedReferable>()

    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) DataLocalReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.textRepresentation())
        else referable

    override fun toDataLocatedReferable(referable: LocatedReferable?): LocatedReferable? =
        if (referable is PsiElement) cache.computeIfAbsent(referable, { DataLocatedReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.precedence, referable.textRepresentation(), toDataLocatedReferable(referable.locatedReferableParent), referable.isTypecheckable) })
        else referable
}
