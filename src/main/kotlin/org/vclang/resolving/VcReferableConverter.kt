package org.vclang.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.reference.converter.SimpleReferableConverter
import org.vclang.psi.VcClassField
import org.vclang.psi.VcFieldDefIdentifier
import org.vclang.psi.VcFile
import org.vclang.psi.ext.PsiReferable


class VcReferableConverter(private val project: Project, private val state: SimpleReferableConverter) : ReferableConverter {
    private val cache = HashMap<PsiElement, TCReferable?>()

    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) DataLocalReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.textRepresentation())
        else referable

    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? =
        when (referable) {
            is VcFile -> null
            is PsiReferable -> {
                if (referable is VcClassField || referable is VcFieldDefIdentifier) {
                    cache.computeIfAbsent(referable) { state[referable] } ?: run {
                        toDataLocatedReferable(referable.locatedReferableParent)
                        cache[referable]
                    }
                } else {
                    val result = cache.computeIfAbsent(referable) { state.computeIfAbsent(referable) {
                        val pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable)
                        val locatedParent = referable.locatedReferableParent
                        val parent = if (locatedParent is VcFile) ModuleReferable(locatedParent.modulePath) else toDataLocatedReferable(locatedParent)
                        when (referable) {
                            is ClassReferable -> ClassDataLocatedReferable(pointer, referable, parent, ArrayList(), ArrayList(), null)
                            is VcClassField, is VcFieldDefIdentifier -> cache[referable]
                            else -> DataLocatedReferable(pointer, referable, parent, toDataLocatedReferable(referable.getTypeClassReference()) as? TCClassReferable)
                        }
                    } }

                    if (referable is ClassReferable && result is ClassDataLocatedReferable && !result.filledIn) {
                        result.underlyingClass = toDataLocatedReferable(referable.underlyingReference) as? TCClassReferable
                        result.superClassReferences.clear()
                        for (ref in referable.superClassReferences) {
                            (toDataLocatedReferable(ref) as? TCClassReferable)?.let { result.superClassReferences.add(it) }
                        }
                        result.fieldReferables.clear()
                        for (ref in referable.fieldReferables) {
                            if (ref is LocatedReferable && ref is PsiReferable) {
                                cache.computeIfAbsent(ref) { state.computeIfAbsent(ref) {
                                    FieldDataLocatedReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(ref), ref, result, toDataLocatedReferable(ref.getTypeClassReference()) as? TCClassReferable, (ref.underlyingReference as? PsiElement)?.let { cache[it] })
                                } }?.let { result.fieldReferables.add(it) }
                            }
                        }
                        result.filledIn = true
                    }

                    result
                }
            }
            is TCReferable -> referable
            else -> null
        }

    /*
    private fun fillInClass(classRef: ClassReferable, tcClassRef: ClassDataLocatedReferable) {
        tcClassRef.superClassReferences.clear()
        for (ref in classRef.superClassReferences) {
            (toDataLocatedReferable(ref) as? TCClassReferable)?.let { tcClassRef.superClassReferences.add(it) }
        }

        tcClassRef.fieldReferables.clear()
        for (ref in classRef.fieldReferables) {
            if (ref is LocatedReferable && ref is PsiReferable) {
                tcClassRef.fieldReferables.add(cache.computeIfAbsent(ref, { state.computeIfAbsent(ref, {
                    DataLocatedReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(ref), ref, tcClassRef, toDataLocatedReferable(ref.getTypeClassReference()) as? TCClassReferable)
                }) }))
            }
        }
    }
    */
}
