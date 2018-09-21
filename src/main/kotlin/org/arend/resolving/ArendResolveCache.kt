package org.arend.resolving

import com.intellij.util.containers.ContainerUtil
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendReferenceElement
import java.util.concurrent.ConcurrentMap

class ArendResolveCache {
    companion object {
        private val map : ConcurrentMap<ArendReferenceElement, Referable> =
                ContainerUtil.createConcurrentWeakKeySoftValueMap(100, 0.75f,
                        Runtime.getRuntime().availableProcessors(), ContainerUtil.canonicalStrategy())


        fun resolveCached(resolver: (ArendReferenceElement) -> Referable?, ref : ArendReferenceElement) : Referable? {
            var result = map[ref]

            if (result == null) {
                result = resolver(ref)
                if (result != null) {
                    map[ref] = result
                }
            }

            return result
        }

        fun clearCache() {
            map.clear()
        }

    }
}