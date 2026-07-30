package academy.backend.pollbot.tierlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs tier-list image rendering on a dedicated pool of platform threads (image work is CPU-bound,
 * so it stays off the per-chat virtual threads). The request queue is bounded at
 * {@link #QUEUE_CAPACITY} and non-evicting: once it is full, {@link #submit} refuses new work
 * (returns {@code false}) instead of dropping already-queued requests.
 */
public final class TierListGenerator {

    private static final Logger log = LoggerFactory.getLogger(TierListGenerator.class);

    private static final int QUEUE_CAPACITY = 100;
    private static final int POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);

    private final ThreadPoolExecutor executor;

    public TierListGenerator() {
        AtomicInteger threadCounter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                POOL_SIZE, POOL_SIZE,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "tierlist-gen-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("Tier-list generation queue is full ({} capacity), rejecting request", QUEUE_CAPACITY);
            return false;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
