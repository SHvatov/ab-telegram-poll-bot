package academy.backend.pollbot.core.concurrency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatSequencer {

    private static final Duration IDLE_EVICTION = Duration.ofMinutes(15);

    private final Cache<Long, Lane> lanes = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    public void execute(long chatId, Runnable task) {
        lanes.get(chatId, id -> new Lane()).submit(task);
    }

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

                if (!mailbox.isEmpty()) {
                    scheduleIfIdle();
                }
            }
        }
    }
}
