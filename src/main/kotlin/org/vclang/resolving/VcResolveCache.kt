package org.vclang.resolving

import com.intellij.util.containers.ContainerUtil
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.ext.VcReferenceElement
import java.util.concurrent.ConcurrentMap

class VcResolveCache {
    companion object {
        private val map : ConcurrentMap<VcReferenceElement, Referable> =
                ContainerUtil.createConcurrentWeakKeySoftValueMap(100, 0.75f,
                        Runtime.getRuntime().availableProcessors(), ContainerUtil.canonicalStrategy())


        fun resolveCached(resolver: (VcReferenceElement) -> Referable?, ref : VcReferenceElement) : Referable? {
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