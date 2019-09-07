package org.arend.resolving

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCClassReferable
import org.arend.psi.ext.ArendReferenceElement
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentMap

class ArendResolveCache(project: Project) {
    private val typeCheckingService = project.service<TypeCheckingService>()
    private val refMap: ConcurrentMap<ArendReferenceElement, Referable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

    fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, reference: ArendReferenceElement): Referable? {
        val globalRef = refMap[reference]
        if (globalRef != null) {
            return if (globalRef == TCClassReferable.NULL_REFERABLE) null else globalRef
        }

        val result = resolver(reference)
        if (result == null && !typeCheckingService.isInitialized) {
            return null
        }

        refMap[reference] = result ?: TCClassReferable.NULL_REFERABLE
        return result
    }

    fun replaceCache(newRef: Referable?, reference: ArendReferenceElement) =
        refMap.put(reference, newRef ?: TCClassReferable.NULL_REFERABLE)

    fun clear() {
        refMap.clear()
    }
}