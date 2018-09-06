package com.jetbrains.arend.ide.resolving

import com.intellij.util.containers.ContainerUtil
import com.jetbrains.arend.ide.psi.ext.ArdReferenceElement
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import java.util.concurrent.ConcurrentMap

class ArdResolveCache {
    companion object {
        private val MAP: ConcurrentMap<ArdReferenceElement, Referable> =
                ContainerUtil.createConcurrentWeakKeySoftValueMap(100, 0.75f,
                        Runtime.getRuntime().availableProcessors(), ContainerUtil.canonicalStrategy())


        fun resolveCached(resolver: (ArdReferenceElement) -> Referable?, ref: ArdReferenceElement): Referable? {
            var result = MAP[ref]

            if (result == null) {
                result = resolver(ref)
                if (result != null) {
                    MAP[ref] = result
                }
            }

            return result
        }

        fun clearCache() {
            MAP.clear()
        }

    }
}