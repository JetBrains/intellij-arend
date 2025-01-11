package org.arend.toolWindow.errors.tree

import org.arend.ext.error.GeneralError
import org.arend.injection.actions.NormalizationCache

class ArendErrorTreeElement(val errors: MutableList<GeneralError>) {
    constructor() : this(ArrayList())

    constructor(error: GeneralError) : this(mutableListOf(error))

    val sampleError: GeneralError
        get() = errors.first()

    // TODO[server2]: What's that?
    val normalizationCache: NormalizationCache = NormalizationCache()

    internal fun enrichNormalizationCache(other: ArendErrorTreeElement) {
        normalizationCache.enrich(other.normalizationCache)
    }

    val highestError: GeneralError
        get() {
            var result = errors.first()
            for (error in errors) {
                if (error.level > result.level) {
                    result = error
                }
            }
            return result
        }

    fun addError(arendError: GeneralError) {
        errors.add(arendError)
    }
}