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
import org.arend.psi.ext.ArendReferenceElement
import org.arend.typechecking.TypeCheckingService
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.locks.ReentrantLock

// TODO[server2]: Delete this class
@Service(Service.Level.PROJECT)
class ArendResolveCache {
    private val refMap: ConcurrentMap<ArendReferenceElement, Referable> = ContainerUtil.createConcurrentWeakKeySoftValueMap()
    private val refMapPsi: ConcurrentMap<ArendReferenceElement, Pair<Int, SmartPsiElementPointer<PsiElement>>> = ContainerUtil.createConcurrentWeakMap()

    private val lock = ReentrantLock()

    fun getCached(reference: ArendReferenceElement): Referable? {
        lock.withLock {
            val ref = refMap[reference]
            if (ref != null/* && ref != TCDefReferable.NULL_REFERABLE*/) return ref

            val entry = refMapPsi[reference]
            val psi = entry?.second?.element
            val code = entry?.first

            if (code != null && code != reference.text.hashCode() || psi != null && !psi.isValid) { // Cached value is probably incorrect/broken
                dropCache(reference)
                return null
            }

            if (psi is Referable) {
                return psi
            }

            return null
        }
    }

    fun replaceCache(newRef: Referable?, reference: ArendReferenceElement) {
        if (newRef is PsiElement && newRef.isValid) {
            lock.withLock {
                refMapPsi[reference] = Pair(reference.text.hashCode(), SmartPointerManager.createPointer(newRef))
                refMap.remove(reference)
            }
        } else if (newRef != null) {
            refMap[reference] = newRef
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
