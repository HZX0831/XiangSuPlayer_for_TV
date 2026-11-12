package com.cutedino.xiangsuplayer.core.source.builtin

import com.cutedino.xiangsuplayer.core.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AggregateSearchTest {
    @Test fun slowProviderIsCancelledAndSuccessfulMiguResultsAreReturned() = runBlocking {
        val stopped = CompletableDeferred<Unit>()
        val migu = Song("id", "Track", "", "", source = "mg")
        val results = BuiltInSourceSearchApi.aggregateSearch(
            listOf(
                {
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                },
                { throw IOException("provider offline") },
                { listOf(migu) }
            ),
            providerTimeoutMs = 100
        )
        assertEquals(listOf(migu), results)
        assertTrue(stopped.isCompleted)
    }

    @Test fun allProvidersFailingIsDifferentFromNoMatches() = runBlocking {
        val failed: suspend () -> List<Song> = { throw IOException("provider offline") }
        val failure = runCatching { BuiltInSourceSearchApi.aggregateSearch(listOf(failed, failed)) }
        assertTrue(failure.exceptionOrNull() is IOException)
        assertEquals(emptyList<Song>(), BuiltInSourceSearchApi.aggregateSearch(listOf(failed, { emptyList() })))
    }
}
