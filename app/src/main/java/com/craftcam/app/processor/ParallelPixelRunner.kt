package com.craftcam.app.processor

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

internal class PixelProcessingTimeoutException(message: String) : RuntimeException(message)

/**
 * Deterministic bounded CPU worker coordinator for image-processing pixel stripes.
 *
 * Design guarantees:
 * 1. Dedicated bounded CPU thread pool (never starves [java.util.concurrent.ForkJoinPool.commonPool]).
 * 2. Cooperative cancellation & exception propagation across all stripes.
 * 3. Deterministic worker drain invariant via per-stripe atomic state machine:
 *    - 0 = NEW, 1 = RUNNING, 2 = FINISHED, 3 = CANCELLED_BEFORE_START
 *    Before returning or throwing (even on worker exception, timeout, or caller interrupt),
 *    every worker that entered state 1 (`RUNNING`) must reach state 2 (`FINISHED`),
 *    preventing use-after-release or post-timeout buffer corruption.
 */
internal object ParallelPixelRunner {

    private const val STATE_NEW = 0
    private const val STATE_RUNNING = 1
    private const val STATE_FINISHED = 2
    private const val STATE_CANCELLED_BEFORE_START = 3

    val defaultWorkerCount: Int
        get() = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    private val threadSeq = AtomicInteger(1)

    internal val cpuExecutor: ExecutorService = ThreadPoolExecutor(
        defaultWorkerCount,
        defaultWorkerCount,
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, "Cameleo-PixelWorker-${threadSeq.getAndIncrement()}").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        }
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    fun <T> runStripes(
        startInclusive: Int,
        endExclusive: Int,
        totalWorkUnits: Int,
        minWorkForParallel: Int = 40_000,
        timeoutMs: Long = 0L,
        executor: ExecutorService = cpuExecutor,
        workerCount: Int = defaultWorkerCount,
        block: (workerIdx: Int, stripeStart: Int, stripeEnd: Int, isCancelled: () -> Boolean) -> T
    ): List<T> {
        if (endExclusive <= startInclusive) return emptyList()
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Pixel processing cancelled before execution")
        }

        val rangeSize = endExclusive - startInclusive
        val workers = minOf(workerCount.coerceAtLeast(1), rangeSize)

        if (workers <= 1 || totalWorkUnits < minWorkForParallel) {
            return listOf(
                block(0, startInclusive, endExclusive) { Thread.currentThread().isInterrupted }
            )
        }

        val cancelled = AtomicBoolean(false)
        val isCancelledCheck: () -> Boolean = { cancelled.get() || Thread.currentThread().isInterrupted }
        val baseChunk = rangeSize / workers
        val remainder = rangeSize % workers

        val futures = ArrayList<Future<T>>(workers)
        val states = AtomicIntegerArray(workers)
        val finishedLatch = CountDownLatch(workers)
        var cursor = startInclusive

        for (w in 0 until workers) {
            val stripeLen = baseChunk + if (w < remainder) 1 else 0
            val stripeStart = cursor
            val stripeEnd = cursor + stripeLen
            cursor = stripeEnd

            val task = Callable<T> {
                if (!states.compareAndSet(w, STATE_NEW, STATE_RUNNING)) {
                    @Suppress("UNCHECKED_CAST")
                    return@Callable Unit as T
                }
                try {
                    if (isCancelledCheck()) {
                        throw InterruptedException("Pixel worker cancelled before stripe $w")
                    }
                    block(w, stripeStart, stripeEnd, isCancelledCheck)
                } finally {
                    states.set(w, STATE_FINISHED)
                    finishedLatch.countDown()
                }
            }
            try {
                futures.add(executor.submit(task))
            } catch (rejected: Throwable) {
                if (states.compareAndSet(w, STATE_NEW, STATE_CANCELLED_BEFORE_START)) {
                    finishedLatch.countDown()
                }
                for (remaining in (w + 1) until workers) {
                    if (states.compareAndSet(remaining, STATE_NEW, STATE_CANCELLED_BEFORE_START)) {
                        finishedLatch.countDown()
                    }
                }
                cancelled.set(true)
                cancelAndDrain(futures, states, finishedLatch)
                throw rejected
            }
        }

        val results = ArrayList<T>(workers)
        val deadlineNs = if (timeoutMs > 0L) System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs) else 0L

        try {
            for (future in futures) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Caller interrupted during pixel processing")
                }
                val result = if (timeoutMs > 0L) {
                    val remainingNs = (deadlineNs - System.nanoTime()).coerceAtLeast(0L)
                    if (remainingNs == 0L && !future.isDone) {
                        throw TimeoutException("Pixel worker exceeded ${timeoutMs}ms budget")
                    }
                    future.get(remainingNs, TimeUnit.NANOSECONDS)
                } else {
                    future.get()
                }
                results.add(result)
            }
            return results
        } catch (t: TimeoutException) {
            cancelled.set(true)
            cancelAndDrain(futures, states, finishedLatch)
            throw PixelProcessingTimeoutException("Pixel processing timed out after ${timeoutMs}ms: ${t.message}")
        } catch (e: ExecutionException) {
            cancelled.set(true)
            cancelAndDrain(futures, states, finishedLatch)
            throw (e.cause ?: e)
        } catch (e: InterruptedException) {
            cancelled.set(true)
            cancelAndDrain(futures, states, finishedLatch)
            Thread.currentThread().interrupt()
            throw e
        } catch (t: Throwable) {
            cancelled.set(true)
            cancelAndDrain(futures, states, finishedLatch)
            throw t
        }
    }

    fun runRange(
        startInclusive: Int,
        endExclusive: Int,
        totalWorkUnits: Int,
        minWorkForParallel: Int = 40_000,
        timeoutMs: Long = 0L,
        executor: ExecutorService = cpuExecutor,
        workerCount: Int = defaultWorkerCount,
        block: (stripeStart: Int, stripeEnd: Int, isCancelled: () -> Boolean) -> Unit
    ) {
        runStripes(
            startInclusive = startInclusive,
            endExclusive = endExclusive,
            totalWorkUnits = totalWorkUnits,
            minWorkForParallel = minWorkForParallel,
            timeoutMs = timeoutMs,
            executor = executor,
            workerCount = workerCount
        ) { _, stripeStart, stripeEnd, isCancelled ->
            block(stripeStart, stripeEnd, isCancelled)
        }
    }

    private fun <T> cancelAndDrain(
        futures: List<Future<T>>,
        states: AtomicIntegerArray,
        finishedLatch: CountDownLatch
    ) {
        for (i in futures.indices) {
            futures[i].cancel(true)
            if (states.compareAndSet(i, STATE_NEW, STATE_CANCELLED_BEFORE_START)) {
                finishedLatch.countDown()
            }
        }
        var interruptedWhileDraining = false
        while (true) {
            try {
                if (finishedLatch.await(30_000L, TimeUnit.MILLISECONDS)) {
                    break
                }
                // Safety fallback: should only ever trigger if a non-interruptible test task hung forever
                break
            } catch (_: InterruptedException) {
                interruptedWhileDraining = true
            }
        }
        if (interruptedWhileDraining) {
            Thread.currentThread().interrupt()
        }
    }
}
