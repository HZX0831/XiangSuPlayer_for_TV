package com.cutedino.xiangsuplayer.data.auth

import android.util.Log
import com.cutedino.xiangsuplayer.data.db.SongDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.moriafly.ncm.NcmApi

internal class FavoriteSyncWorker(
    private val dao: SongDao,
    scope: CoroutineScope,
    currentAccount: () -> NcmApi.AccountIdentity? = NcmApi::currentAccount,
    syncLike: suspend (NcmApi.AccountIdentity, Long, Boolean) -> Result<Unit> = NcmApi::setAccountLike,
    now: () -> Long = System::currentTimeMillis
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                try {
                    val account = currentAccount()
                    val entry = account?.let { dao.nextFavoriteSync(it.userId, now()) }
                    if (entry == null) {
                        withTimeoutOrNull(5_000L) { wake.receive() }
                    } else {
                        val result = withTimeoutOrNull(12_000L) {
                            syncLike(account, entry.trackId, entry.liked)
                        }
                        if (result?.isSuccess == true) {
                            dao.finishFavoriteSync(entry.accountId, entry.trackId, entry.token)
                        } else {
                            val attempts = (entry.attempts + 1).coerceAtMost(10)
                            val delayMs = minOf(300_000L, 1_000L shl attempts)
                            dao.deferFavoriteSync(entry.accountId, entry.trackId, entry.token, attempts,
                                now() + delayMs)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("FavoriteSync", "Could not process pending favorite", failure)
                    delay(5_000L)
                }
            }
        }
    }

    fun notifyChanged() { wake.trySend(Unit) }
}
