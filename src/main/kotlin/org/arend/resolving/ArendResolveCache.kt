package org.arend.resolving

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.containers.ContainerUtil
import okio.withLock
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.ArendReferenceElement
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock

@Service(Service.Level.PROJECT)
class ArendResolveCache(project: Project) {
    private val typeCheckingService = project.service<TypeCheckingService>()
    private val refMap: ConcurrentMap<ArendReferenceElement, Referable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()
    private val refMapPsi: ConcurrentMap<ArendReferenceElement, Pair<Int, SmartPsiElementPointer<PsiElement>>> = ContainerUtil.createConcurrentWeakMap()
    private val lock = ReentrantLock()

    /**
     * Retrieves a cached value associated with the given `ArendReferenceElement`.
     * It checks if the reference exists in the cache and returns the associated value and a boolean indicating
     * whether the value was successfully retrieved from the cache (even if it is `null`).
     * If the cached reference is invalid or broken, the cache is cleared for the reference, and the method
     * returns `null` with `false`.
     *
     * @param reference The `ArendReferenceElement` for which the cached value should be retrieved.
     * @return A pair where the first element is a `Referable?` representing the cached value (or `null`),
     * and the second element is a `Boolean` indicating whether a value (including `null`) was successfully
     * retrieved from the cache.
     */
    fun getCached(reference: ArendReferenceElement): Pair<Referable?, Boolean> {
        lock.withLock {
            val ref = refMap[reference]
            if (ref != null)
                return if (ref == TCDefReferable.NULL_REFERABLE) Pair(null, true) else Pair(ref, true)

            val entry = refMapPsi[reference]
            val psi = entry?.second?.element
            val code = entry?.first

            if (code != null && code != reference.text.hashCode() || psi != null && !psi.isValid) {
                dropCache(reference)
                return Pair(null, false)
            }

            if (psi is Referable) {
                return Pair(psi, true)
            }

            return Pair(null, false)
        }
    }

    fun resolveCached(resolver: () -> Referable?, reference: ArendReferenceElement): Referable? {
        val pair = getCached(reference)
        if (pair.second) return pair.first

        val result = resolver()
        if (result == null && !typeCheckingService.isInitialized) {
            lock.withLock {
                refMap[reference] = TCDefReferable.NULL_REFERABLE
            }
            return null
        }

        doReplaceCache(result, reference)

        return result
    }

    fun replaceCache(newRef: Referable?, reference: ArendReferenceElement): Referable? {
        val pair = getCached(reference)
        doReplaceCache(newRef, reference)
        return pair.first
    }

    private fun doReplaceCache(newRef: Referable?, reference: ArendReferenceElement) {
        lock.withLock {
            if (newRef is PsiElement && newRef.isValid) {
                refMapPsi[reference] = Pair(reference.text.hashCode(), SmartPointerManager.createPointer(newRef))
                refMap.remove(reference)
            } else if (newRef != null) {
                refMap[reference] = newRef
            } else {
                refMap[reference] = TCDefReferable.NULL_REFERABLE
            }
        }
    }

    fun dropCache(reference: ArendReferenceElement) {
        lock.withLock {
            refMap.remove(reference)
            refMapPsi.remove(reference)
        }
    }

    fun clear() {
        lock.withLock {
            refMap.clear()
            refMapPsi.clear()
        }
    }
}
