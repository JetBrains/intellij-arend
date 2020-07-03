package org.arend.resolving

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.*
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable


class ArendReferableConverter(private val project: Project?, private val state: SimpleReferableConverter) : BaseReferableConverter() {
    private val cache = HashMap<PsiElement, TCReferable?>()

    fun clearCache() {
        cache.clear()
    }

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
                var result = cache[referable]
                if (result == null) {
                    result = state[referable]
                    if (result == null) {
                        val pointer: SmartPsiElementPointer<PsiElement>? = project?.let { SmartPointerManager.getInstance(it).createSmartPsiElementPointer(referable) }
                        val locatedParent = referable.locatedReferableParent
                        val parent = if (locatedParent is ArendFile) locatedParent.moduleLocation?.let { FullModuleReferable(it) } else toDataLocatedReferable(locatedParent)
                        result = if (referable is FieldReferable) FieldDataLocatedReferable(pointer, referable, parent) else DataLocatedReferable(pointer, referable, parent)
                        val prev = state.putIfAbsent(referable, result)
                        if (prev != null) {
                            result = prev
                        }
                    }
                    cache[referable] = result
                }
                result
            }
            is TCReferable -> referable
            else -> null
        }
}
