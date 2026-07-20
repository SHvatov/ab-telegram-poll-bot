package academy.backend.pollbot.core.concurrency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChatSequencer {

    private static final Duration IDLE_EVICTION = Duration.ofMinutes(15);

    // Caps how many pending tasks a single chat can queue up, so one abusive or malfunctioning
    // chat hammering the bot faster than it can be processed can't grow its mailbox without
    // bound and exhaust memory. Once full, new tasks for that chat are dropped (and logged)
    // rather than accepted.
    private static final int MAX_MAILBOX_SIZE = 256;

    private final Cache<Long, ChatActor> actors = Caffeine.newBuilder()
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    public void execute(long chatId, Runnable task) {
        actors.get(chatId, id -> new ChatActor(id)).submit(task);
    }

    public void shutdown() {
    }

    private static final class ChatActor {

        private static final Logger log = LoggerFactory.getLogger(ChatActor.class);

        private static final int IDLE = 0;
        private static final int RUNNING = 1;

        private final long chatId;
        private final Queue<Runnable> mailbox = new LinkedBlockingQueue<>(MAX_MAILBOX_SIZE);
        private final AtomicInteger state = new AtomicInteger(IDLE);

        ChatActor(long chatId) {
            this.chatId = chatId;
        }

        void submit(Runnable task) {
            if (!mailbox.offer(task)) {
                log.warn("Mailbox full ({} pending) for chat {}, dropping task", MAX_MAILBOX_SIZE, chatId);
                return;
            }
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
