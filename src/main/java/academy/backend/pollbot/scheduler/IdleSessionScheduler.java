package academy.backend.pollbot.scheduler;

import academy.backend.pollbot.core.concurrency.ChatSequencer;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.telegram.routing.BotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically bounces sessions that have gone quiet in a non-MENU state back to the main menu, so
 * a user who wandered off mid-flow returns to a clean starting point instead of a stale screen.
 */
public final class IdleSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdleSessionScheduler.class);

    private static final long IDLE_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final long TICK_INTERVAL_SECONDS = 60;

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "idle-session-ticker");
        thread.setDaemon(true);
        return thread;
    });

    private final ChatSequencer chatSequencer;
    private final ChatViewRepository chatViewRepository;
    private final BotService botService;

    public IdleSessionScheduler(ChatSequencer chatSequencer, ChatViewRepository chatViewRepository,
                                BotService botService) {
        this.chatSequencer = chatSequencer;
        this.chatViewRepository = chatViewRepository;
        this.botService = botService;
    }

    public void start() {
        ticker.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Idle session scheduler started, timeout={}ms, interval={}s", IDLE_TIMEOUT_MILLIS, TICK_INTERVAL_SECONDS);
    }

    public void stop() {
        ticker.shutdownNow();
    }

    private void tick() {
        List<CurrentChatState> states;
        try {
            states = chatViewRepository.listIdleStates(IDLE_TIMEOUT_MILLIS);
        } catch (RuntimeException e) {
            log.error("Failed to list idle chat states", e);
            return;
        }
        for (CurrentChatState state : states) {
            chatSequencer.execute(state.chatId(), () -> resetSafely(state));
        }
    }

    private void resetSafely(CurrentChatState state) {
        try {
            botService.resetToMenu(state);
        } catch (RuntimeException e) {
            log.warn("Failed to reset idle chat {} to menu", state.chatId(), e);
        }
    }
}
