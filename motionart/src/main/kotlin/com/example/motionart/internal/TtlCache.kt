package com.example.motionart.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A time-bounded cache that remembers absences as deliberately as it remembers values.
 *
 * Most tracks have no motion cover, so "there is nothing" is the answer we compute most often. An
 * ordinary cache would discard it and re-ask the network every time the same track came round in
 * the queue, which is the worst case dressed up as the common one.
 *
 * The lock is held across the fetch rather than just around the map. Several surfaces can ask for
 * the same track at the same moment, and letting each of them fire its own request to learn the
 * same answer is exactly what this is meant to prevent.
 */
internal class TtlCache<K : Any, V : Any>(
    private val ttlMillis: Long,
    private val maxEntries: Int = 256,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V?, val storedAt: Long)

    private val mutex = Mutex()

    private val entries = object : LinkedHashMap<K, Entry<V>>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>) = size > maxEntries
    }

    /** The cached value for [key], computing and storing it — hit or miss — when it has lapsed. */
    suspend fun get(key: K, compute: suspend () -> V?): V? = mutex.withLock {
        val now = clock()
        val cached = entries[key]
        if (cached != null && now - cached.storedAt < ttlMillis) return@withLock cached.value
        val fresh = compute()
        entries[key] = Entry(fresh, now)
        fresh
    }

    /** Forget [key], so the next read recomputes it. Used when a credential turns out to be stale. */
    suspend fun invalidate(key: K) = mutex.withLock { entries.remove(key); Unit }

    suspend fun clear() = mutex.withLock { entries.clear() }
}
