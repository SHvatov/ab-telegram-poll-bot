package academy.backend.pollbot.telegram.ui;

import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.routing.CallbackProtocol;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

public final class MenuFlow {

    private static final String SOURCE_REPOSITORY_URL = "https://github.com/SHvatov/ab-telegram-poll-bot";

    private final TelegramGateway gateway;
    private final Localization localization;
    private final ChatViewRepository chatViewRepository;

    public MenuFlow(TelegramGateway gateway, Localization localization, ChatViewRepository chatViewRepository) {
        this.gateway = gateway;
        this.localization = localization;
        this.chatViewRepository = chatViewRepository;
    }

    public void showMainMenu(long chatId, long userId) {
        render(chatId, userId, localization.get("menu.title"));
    }

    public void showMainMenuWithGreeting(long chatId, long userId) {
        String text = localization.get("welcome.greeting") + "\n\n" + localization.get("menu.title");
        render(chatId, userId, text);
    }

    public void showUnknownCommand(long chatId, long userId) {
        String text = localization.get("error.unknown-command") + "\n\n" + localization.get("menu.title");
        render(chatId, userId, text);
    }

    private void render(long chatId, long userId, String text) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        int messageId = gateway.renderText(chatId, previous, text, mainMenuKeyboard());
        setState(chatId, ChatState.MENU, messageId, userId);
    }

    public void showNotImplemented(long chatId, long userId) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        InlineKeyboardMarkup keyboard = Keyboards.singleButtonKeyboard(
                localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU);
        int messageId = gateway.renderText(chatId, previous, localization.get("menu.not-implemented"), keyboard);
        setState(chatId, ChatState.MENU, messageId, userId);
    }

    public void showSource(long chatId, long userId) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU)),
                new InlineKeyboardRow(Keyboards.urlButton(localization.get("menu.button.source"), SOURCE_REPOSITORY_URL))
        )).build();
        int messageId = gateway.renderText(chatId, previous, localization.get("menu.source.text"), keyboard);
        setState(chatId, ChatState.MENU, messageId, userId);
    }

    private InlineKeyboardMarkup mainMenuKeyboard() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.vote"), CallbackProtocol.SHOW_VOTE_LIST)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.rating"), CallbackProtocol.SHOW_RATING_LIST)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.my-tier-list"), CallbackProtocol.MY_TIER_LIST)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.global-tier-list"), CallbackProtocol.GLOBAL_TIER_LIST)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.source"), CallbackProtocol.SOURCE))
        )).build();
    }

    /** Persists the screen and decides whether it belongs in the scheduler's refresh set. */
    private void setState(long chatId, ChatState state, int messageId, long userId) {
        chatViewRepository.setState(chatId, state, messageId, userId);
        if (state.isRefreshable()) {
            chatViewRepository.markAsRefreshable(chatId);
        } else {
            chatViewRepository.unmarkAsRefreshable(chatId);
        }
    }
}
