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
