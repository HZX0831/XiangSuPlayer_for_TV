package com.cutedino.xiangsuplayer.core.library

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stable directory snapshots shared by all pages during a browse session. */
internal class FolderListingCache<T>(
    private val nanoTime: () -> Long = System::nanoTime,
    private val ttlNanos: Long = 60_000_000_000L
) {
    private data class Cached<T>(val value: T, val createdAt: Long)
    private val entries = LinkedHashMap<Pair<String, String?>, Cached<T>>(16, 0.75f, true)
    private val lock = Mutex()

    suspend fun get(folder: String, directory: String?, load: suspend () -> T): T = lock.withLock {
        val key = folder to directory
        entries[key]?.takeIf { nanoTime() - it.createdAt < ttlNanos }?.let { return@withLock it.value }
        val value = load()
        entries[key] = Cached(value, nanoTime())
        if (entries.size > 12) entries.remove(entries.keys.first())
        value
    }

    suspend fun invalidate(folder: String) {
        lock.withLock { entries.keys.removeAll { it.first == folder } }
    }
}
