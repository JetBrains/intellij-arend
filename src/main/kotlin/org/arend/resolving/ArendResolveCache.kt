package org.arend.resolving

import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCClassReferable
import org.arend.psi.ext.ArendReferenceElement
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentMap

interface ArendResolveCache {
    fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, reference: ArendReferenceElement) : Referable?
    fun replaceCache(newRef: Referable?, reference: ArendReferenceElement): Referable?
    fun clear()
}

class ArendResolveCacheImpl(project: Project) : ArendResolveCache {
    private val typeCheckingService = TypeCheckingService.getInstance(project)
    private val refMap: ConcurrentMap<ArendReferenceElement, Referable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

    override fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, reference: ArendReferenceElement): Referable? {
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

    override fun replaceCache(newRef: Referable?, reference: ArendReferenceElement) =
        refMap.put(reference, newRef ?: TCClassReferable.NULL_REFERABLE)

    override fun clear() {
        refMap.clear()
    }
}