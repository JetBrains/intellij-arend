package org.vclang.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.reference.converter.SimpleReferableConverter


class VcReferableConverter(private val project: Project, private val state: SimpleReferableConverter) : ReferableConverter {
    private val cache = HashMap<PsiElement, TCReferable>()

    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) DataLocalReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.textRepresentation())
        else referable

    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? =
        when (referable) {
            is PsiElement -> cache.computeIfAbsent(referable, { state.computeIfAbsent(referable, {
                val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable)
                val parent = toDataLocatedReferable(referable.locatedReferableParent)
                if (referable is ClassReferable) ClassDataLocatedReferable(pointer, referable, parent, referable.superClassReferences.mapNotNull { toDataLocatedReferable(it) as? TCClassReferable }, referable.fieldReferables.mapNotNull { (it as? LocatedReferable)?.let { toDataLocatedReferable(it) } })
                else DataLocatedReferable(pointer, referable, parent)
            }) })
            is TCReferable -> referable
            else -> null
        }
}
