package academy.backend.pollbot.scheduler;

import academy.backend.pollbot.redis.ChatViewState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.telegram.BotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Every {@code refreshIntervalSeconds}, re-renders every currently open vote/rating list so
 * ratings stay up to date for anyone looking at one. The ticker itself is a single lightweight
 * thread; the actual per-chat Telegram calls are fanned out onto the shared virtual-thread pool
 * so a slow chat can't delay the others or the next tick.
 */
public final class ListRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(ListRefreshScheduler.class);

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "list-refresh-ticker");
        thread.setDaemon(true);
        return thread;
    });

    private final ExecutorService virtualThreadExecutor;
    private final ChatViewRepository chatViewRepository;
    private final BotService botService;
    private final int refreshIntervalSeconds;

    public ListRefreshScheduler(ExecutorService virtualThreadExecutor,
                                 ChatViewRepository chatViewRepository,
                                 BotService botService,
                                 int refreshIntervalSeconds) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.chatViewRepository = chatViewRepository;
        this.botService = botService;
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public void start() {
        ticker.scheduleAtFixedRate(this::tick, refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
        log.info("List refresh scheduler started, interval={}s", refreshIntervalSeconds);
    }

    public void stop() {
        ticker.shutdownNow();
    }

    private void tick() {
        List<ChatViewState> views;
        try {
            views = chatViewRepository.listRefreshableViews();
        } catch (RuntimeException e) {
            log.error("Failed to list refreshable chat views", e);
            return;
        }
        for (ChatViewState state : views) {
            virtualThreadExecutor.execute(() -> refreshSafely(state));
        }
    }

    private void refreshSafely(ChatViewState state) {
        try {
            botService.refreshList(state);
        } catch (RuntimeException e) {
            log.warn("Failed to refresh list for chat {}", state.chatId(), e);
        }
    }
}
