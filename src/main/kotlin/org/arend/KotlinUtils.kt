package org.arend

inline fun <T, R : Any> Iterable<T>.mapFirstNotNull(transform: (T) -> R?): R? {
    for (item in this) {
        val r = transform(item)
        if (r != null) {
            return r
        }
    }
    return null
}
