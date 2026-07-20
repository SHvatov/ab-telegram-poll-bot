package academy.backend.pollbot.core.concurrency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChatSequencer {

    private static final Duration IDLE_EVICTION = Duration.ofMinutes(15);

    private final Cache<Long, ChatActor> actors = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    public void execute(long chatId, Runnable task) {
        actors.get(chatId, id -> new ChatActor()).submit(task);
    }

    public void shutdown() {
    }

    private static final class ChatActor {

        private static final Logger log = LoggerFactory.getLogger(ChatActor.class);

        private static final int IDLE = 0;
        private static final int RUNNING = 1;

        private final Queue<Runnable> mailbox = new ConcurrentLinkedQueue<>();
        private final AtomicInteger state = new AtomicInteger(IDLE);

        void submit(Runnable task) {
            mailbox.offer(task);
            schedule();
        }

        private void schedule() {
            if (state.compareAndSet(IDLE, RUNNING)) {
                Thread.startVirtualThread(this::processMailbox);
            }
        }

        private void processMailbox() {
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
                state.set(IDLE);

                if (!mailbox.isEmpty()) {
                    schedule();
                }
            }
        }
    }
}
