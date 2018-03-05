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


        //var pos = 1;
        //var neg = 1;

        fun resolveCached(resolver: (VcReferenceElement) -> Referable?, ref : VcReferenceElement) : Referable? {
            var result = readCache(ref)

            if (result == null) {
                result = resolver(ref)
                if (result != null) cache(ref, result)
            }

            return result
        }

        fun cache(ref : VcReferenceElement, value : Referable) {
            map.put(ref, value)
        }

        fun readCache(ref : VcReferenceElement) : Referable? {
            //if ((pos + neg) % 100 == 0) System.out.println("Ratio: " + pos.toDouble() / (pos + neg) + "; cached: " + pos + "; not cached: " + neg)

            if (map.containsKey(ref)) {
                //pos++;
                return map.get(ref)
            }

            //neg++

            return null
        }

        fun clearCache() {
            map.clear()
        }

    }
}