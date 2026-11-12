package com.cutedino.xiangsuplayer.core.library

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retains one directory snapshot for the lifetime of an open browse session. */
internal class FolderListingCache<T : Any> {
    private var snapshot: T? = null
    private val lock = Mutex()

    suspend fun get(load: suspend () -> T): T = lock.withLock {
        snapshot ?: load().also { snapshot = it }
    }
}
