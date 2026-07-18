package academy.backend.pollbot.telegram.api;

import academy.backend.pollbot.core.concurrency.ChatSequencer;
import academy.backend.pollbot.telegram.routing.BotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * Dispatches every incoming update onto a per-chat virtual-thread lane (see {@link ChatSequencer}):
 * different chats are handled fully in parallel, but updates for the same chat are always
 * processed one at a time, in order.
 */
public final class PollBotUpdateConsumer implements LongPollingUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(PollBotUpdateConsumer.class);

    private final ChatSequencer chatSequencer;
    private final BotService botService;

    public PollBotUpdateConsumer(ChatSequencer chatSequencer, BotService botService) {
        this.chatSequencer = chatSequencer;
        this.botService = botService;
    }

    @Override
    public void consume(List<Update> updates) {
        for (Update update : updates) {
            Long chatId = chatIdOf(update);
            if (chatId != null) {
                chatSequencer.execute(chatId, () -> handle(update));
            }
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

    private static Long chatIdOf(Update update) {
        if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        return null;
    }
}
