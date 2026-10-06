package com.pinkiptv.extreme

/** Select only supplied physical control networks, before sending any request. */
internal fun <T> selectPinkControlNetwork(
    candidates: List<T>,
    preferred: T?,
    validated: (T) -> Boolean,
    resolvesControlHost: (T) -> Boolean,
): T? {
    val ordered = candidates.distinct().sortedBy {
        when {
            it == preferred && validated(it) -> 0
            validated(it) -> 1
            it == preferred -> 2
            else -> 3
        }
    }
    return ordered.firstOrNull(resolvesControlHost)
}
