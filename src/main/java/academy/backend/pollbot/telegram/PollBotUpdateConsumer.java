package academy.backend.pollbot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Dispatches every incoming update to its own virtual thread, so one slow or
 * misbehaving chat can never block processing of the others.
 */
public final class PollBotUpdateConsumer implements LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(PollBotUpdateConsumer.class);

    private final ExecutorService virtualThreadExecutor;
    private final BotService botService;

    public PollBotUpdateConsumer(ExecutorService virtualThreadExecutor, BotService botService) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.botService = botService;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            virtualThreadExecutor.execute(() -> handle(update));
        }
    }

    private void handle(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                botService.handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()
                    && update.getMessage().getText().startsWith("/start")) {
                botService.handleStart(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Unhandled error while processing update {}", update.getUpdateId(), e);
        }
    }
}
