package org.arend.resolving

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.ArendReferenceElement
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentMap

class ArendResolveCache(project: Project) {
    private val typeCheckingService = project.service<TypeCheckingService>()
    private val refMap: ConcurrentMap<ArendReferenceElement, Referable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

    fun getCached(reference: ArendReferenceElement): Referable? {
        val ref = refMap[reference]
        return if (ref == TCDefReferable.NULL_REFERABLE) null else ref
    }

    fun resolveCached(resolver: () -> Referable?, reference: ArendReferenceElement): Referable? {
        val globalRef = refMap[reference]
        if (globalRef == TCDefReferable.NULL_REFERABLE) {
            return null
        }
        if (globalRef != null && (globalRef !is PsiElement || globalRef.isValid)) {
            return globalRef
        }

        val result = resolver()
        if (result == null && !typeCheckingService.isInitialized) {
            return null
        }

        refMap[reference] = result ?: TCDefReferable.NULL_REFERABLE
        return result
    }

    fun replaceCache(newRef: Referable?, reference: ArendReferenceElement): Referable? {
        return refMap.put(reference, newRef ?: TCDefReferable.NULL_REFERABLE)
    }

    fun dropCache(reference: ArendReferenceElement) {
        refMap.remove(reference)
    }

    fun clear() {
        refMap.clear()
    }
}