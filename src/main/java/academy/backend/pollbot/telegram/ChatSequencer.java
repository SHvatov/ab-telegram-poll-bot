package academy.backend.pollbot.telegram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guarantees that all tasks submitted for the same chat run strictly one after another (FIFO),
 * while different chats still run fully in parallel. This is what makes it safe to process every
 * update on its own virtual thread: two updates for the same chat - whether they land in the same
 * poll batch or a later one while the first is still being handled - can otherwise race on that
 * chat's {@code CurrentChatState} (e.g. both read the same "current message" before either writes
 * its own, corrupting the single evolving message).
 * <p>
 * Each chat gets a {@link Lane}: a lock-free mailbox drained by at most one virtual thread at a
 * time, which starts on demand when work arrives and exits once the mailbox is empty - no thread
 * or executor sits idle between bursts. Lanes for chats that haven't submitted anything in 15
 * minutes are evicted, so the bot's memory footprint tracks recently-active chats, not every chat
 * that has ever written to it.
 */
public final class ChatSequencer {

    private static final Duration IDLE_EVICTION = Duration.ofMinutes(15);

    private final Cache<Long, Lane> lanes = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    public void execute(long chatId, Runnable task) {
        lanes.get(chatId, id -> new Lane()).submit(task);
    }

    /** No-op: lanes only ever hold virtual threads that finish on their own once idle. */
    public void shutdown() {
    }

    private static final class Lane {

        private static final Logger log = LoggerFactory.getLogger(Lane.class);

        private final Queue<Runnable> mailbox = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);

        void submit(Runnable task) {
            mailbox.offer(task);
            scheduleIfIdle();
        }

        private void scheduleIfIdle() {
            if (running.compareAndSet(false, true)) {
                Thread.startVirtualThread(this::drain);
            }
        }

        private void drain() {
            try {
                Runnable task;
                while ((task = mailbox.poll()) != null) {
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        log.error("Unhandled error running a chat task", e);
                    }
                }
            } finally {
                running.set(false);
                // A task may have been offered between our last empty poll() and the line above;
                // if so, re-schedule so it doesn't sit unprocessed until the next submit().
                if (!mailbox.isEmpty()) {
                    scheduleIfIdle();
                }
            }
        }
    }
}
