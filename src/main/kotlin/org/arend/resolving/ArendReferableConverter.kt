package org.arend.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.*
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.psi.ArendClassField
import org.arend.psi.ArendFieldDefIdentifier
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable


class ArendReferableConverter(private val project: Project?, private val state: SimpleReferableConverter) : ReferableConverter {
    private val cache = HashMap<PsiElement, TCReferable?>()

    fun putIfAbsent(referable: PsiLocatedReferable, tcReferable: TCReferable) {
        cache.putIfAbsent(referable, tcReferable)
        state.putIfAbsent(referable, tcReferable)
    }

    override fun toDataReferable(referable: Referable?): Referable? =
        if (referable is PsiElement) {
            if (project != null) {
                DataLocalReferable(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referable), referable.textRepresentation())
            } else {
                LocalReferable(referable.textRepresentation())
            }
        } else {
            referable
        }

    override fun toDataLocatedReferable(referable: LocatedReferable?): TCReferable? =
        when (referable) {
            is ArendFile -> null
            is PsiReferable -> {
                if (referable is ArendClassField || referable is ArendFieldDefIdentifier) {
                    var result = cache[referable]
                    if (result == null) {
                        result = state[referable]
                        if (result == null) {
                            toDataLocatedReferable(referable.locatedReferableParent)
                            result = cache[referable]
                        } else {
                            cache[referable] = result
                        }
                    }
                    result
                } else {
                    var result = cache[referable]
                    if (result == null) {
                        result = state[referable]
                        if (result == null) {
                            val pointer: SmartPsiElementPointer<PsiElement>? = project?.let { SmartPointerManager.getInstance(it).createSmartPsiElementPointer(referable) }
                            val locatedParent = referable.locatedReferableParent
                            val parent = if (locatedParent is ArendFile) locatedParent.modulePath?.let { ModuleReferable(it) } else toDataLocatedReferable(locatedParent)
                            result = when (referable) {
                                is ClassReferable -> ClassDataLocatedReferable(pointer, referable, parent, referable.isRecord, ArrayList(), ArrayList(), ArrayList())
                                is ArendClassField, is ArendFieldDefIdentifier -> cache[referable]
                                else -> DataLocatedReferable(pointer, referable, parent, toDataLocatedReferable(referable.getTypeClassReference()) as? TCClassReferable)
                            }
                            val prev = state.putIfAbsent(referable, result)
                            if (prev != null) {
                                result = prev
                            }
                        }
                        (result as? ClassDataLocatedReferable)?.filledIn = false // If the result was in state, then we need to put the fields to cache again.
                        cache[referable] = result
                    }

                    if (referable is ClassReferable && result is ClassDataLocatedReferable && !result.filledIn) {
                        result.filledIn = true
                        result.superClasses.clear()
                        for (ref in referable.superClassReferences) {
                            (toDataLocatedReferable(ref) as? TCClassReferable)?.let { result.superClasses.add(it) }
                        }
                        result.fieldReferables.clear()
                        for (ref in referable.fieldReferables) {
                            if (ref is FieldReferable && ref is PsiReferable) {
                                var fieldResult = cache[ref]
                                if (fieldResult == null) {
                                    fieldResult = state[ref]
                                    if (fieldResult == null) {
                                        fieldResult = FieldDataLocatedReferable(project?.let { SmartPointerManager.getInstance(it).createSmartPsiElementPointer(ref) }, ref, result, toDataLocatedReferable(ref.getTypeClassReference()) as? TCClassReferable)
                                        val prev = state.putIfAbsent(ref, fieldResult)
                                        if (prev != null) {
                                            fieldResult = prev
                                        }
                                    }
                                    cache[ref] = fieldResult
                                }
                                (fieldResult as? TCFieldReferable)?.let { result.fieldReferables.add(it) }
                            }
                        }
                        result.implementedFields.clear()
                        for (ref in referable.implementedFields) {
                            if (ref is PsiReferable) {
                                cache[ref]?.let { result.implementedFields.add(it)  }
                            }
                        }
                        result.isRecordFlag = referable.isRecord
                    }

                    result
                }
            }
            is TCReferable -> referable
            else -> null
        }
}
