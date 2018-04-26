package org.vclang.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.reference.converter.SimpleReferableConverter
import org.vclang.psi.VcClassField
import org.vclang.psi.VcFieldTele
import org.vclang.psi.VcFile


class VcReferableConverter(private val project: Project, private val state: SimpleReferableConverter) : ReferableConverter {
    private val cache = HashMap<PsiElement, TCReferable>()

    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) DataLocalReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.textRepresentation())
        else referable

    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? =
        when (referable) {
            is VcFile -> null
            is PsiElement -> {
                var superClasses: MutableList<TCClassReferable>? = null
                var fields: MutableList<TCReferable>? = null
                val result = cache.computeIfAbsent(referable, { state.computeIfAbsent(referable, {
                    val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable)
                    val locatedParent = referable.locatedReferableParent
                    val parent = if (locatedParent is VcFile) ModuleReferable(locatedParent.modulePath) else toDataLocatedReferable(locatedParent)
                    when (referable) {
                        is ClassReferable -> {
                            superClasses = ArrayList()
                            fields = ArrayList()
                            ClassDataLocatedReferable(pointer, referable, parent, superClasses!!, fields!!)
                        }
                        is VcClassField, is VcFieldTele -> cache[referable] ?: DataLocatedReferable(pointer, referable, parent)
                        else -> DataLocatedReferable(pointer, referable, parent)
                    }
                }) })

                if (referable is ClassReferable) {
                    for (ref in referable.superClassReferences) {
                        (toDataLocatedReferable(ref) as? TCClassReferable)?.let { superClasses?.add(it) }
                    }
                    for (ref in referable.fieldReferables) {
                        if (ref is LocatedReferable && ref is PsiElement) {
                            fields?.add(DataLocatedReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(ref), ref, result))
                        }
                    }
                }

                result
            }
            is TCReferable -> referable
            else -> null
        }
}
