@file:Suppress("unused")

package me.santio.minehututils.coroutines

import dev.minn.jda.ktx.events.CoroutineEventListener
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.santio.minehututils.scope
import net.dv8tion.jda.api.requests.RestAction
import net.dv8tion.jda.api.utils.concurrent.Task
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration

private val logger = LoggerFactory.getLogger("MinehutUtils-CoroutineScope")

/**
 * Allows for the use of coroutines in JDA
 * Taken from: https://github.com/MinnDevelopment/jda-ktx/blob/master/src/main/kotlin/dev/minn/jda/ktx/coroutines/coroutines.kt
 */

/**
 * Awaits the result of this CompletableFuture
 *
 * @return Result
 */
suspend fun <T> CompletableFuture<T>.await() = suspendCancellableCoroutine<T> {
    it.invokeOnCancellation { cancel(true) }
    whenComplete { r, e ->
        when {
            e != null -> it.resumeWithException(e)
            else -> it.resume(r)
        }
    }
}

/**
 * Awaits the result of this RestAction
 *
 * @return Result
 */
suspend fun <T> RestAction<T>.await(): T = submit().await()

/**
 * Awaits the result of this Task
 *
 * @return Result
 */
suspend fun <T> Task<T>.await() = suspendCancellableCoroutine<T> {
    it.invokeOnCancellation { cancel() }
    onSuccess { r -> it.resume(r) }
    onError { e -> it.resumeWithException(e) }
}

fun CoroutineEventListener.expireAfter(ttl: Duration): CoroutineEventListener {
    val listener = WeakReference(this)
    scope.launch {
        delay(ttl)
        listener.get()?.cancel()
    }
    return this
}

/**
 * A simple exception handler for coroutines, this will log any exceptions thrown in coroutines
 */
val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    logger.error("Exception in coroutine scope ${Thread.currentThread().name}", throwable)
}
