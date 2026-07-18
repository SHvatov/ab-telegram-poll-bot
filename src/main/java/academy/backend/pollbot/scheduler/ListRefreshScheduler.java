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

public final class ListRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(ListRefreshScheduler.class);

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "list-refresh-ticker");
        thread.setDaemon(true);
        return thread;
    });

    private final ChatSequencer chatSequencer;
    private final ChatViewRepository chatViewRepository;
    private final BotService botService;
    private final int refreshIntervalSeconds;

    public ListRefreshScheduler(ChatSequencer chatSequencer,
                                 ChatViewRepository chatViewRepository,
                                 BotService botService,
                                 int refreshIntervalSeconds) {
        this.chatSequencer = chatSequencer;
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
        List<CurrentChatState> states;
        try {
            states = chatViewRepository.listRefreshableStates();
        } catch (RuntimeException e) {
            log.error("Failed to list refreshable chat states", e);
            return;
        }
        for (CurrentChatState state : states) {
            chatSequencer.execute(state.chatId(), () -> refreshSafely(state));
        }
    }

    private void refreshSafely(CurrentChatState state) {
        try {
            botService.refreshList(state);
        } catch (RuntimeException e) {
            log.warn("Failed to refresh list for chat {}", state.chatId(), e);
        }
    }
}
