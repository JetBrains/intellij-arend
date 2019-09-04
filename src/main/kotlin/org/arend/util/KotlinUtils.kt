package org.arend.util

inline fun <T, R> Iterable<T>.mapFirstNotNull(transform: (T) -> R?): R? {
    for (item in this) {
        val r = transform(item)
        if (r != null) {
            return r
        }
    }
    return null
}

inline fun <T, R : Any> Iterable<T>.mapUntilNotNull(transform: (T) -> R?): ArrayList<R> {
    val result = ArrayList<R>()
    for (item in this) {
        result.add(transform(item) ?: return result)
    }
    return result
}
