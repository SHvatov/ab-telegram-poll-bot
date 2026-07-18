package academy.backend.pollbot.telegram;

import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.i18n.Localization;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;
import java.util.Map;

/** The main menu, and the stub screen for the not-yet-implemented tier-list commands. */
final class MenuFlow {

    private final TelegramGateway gateway;
    private final Localization localization;
    private final ChatViewRepository chatViewRepository;

    MenuFlow(TelegramGateway gateway, Localization localization, ChatViewRepository chatViewRepository) {
        this.gateway = gateway;
        this.localization = localization;
        this.chatViewRepository = chatViewRepository;
    }

    void showMainMenu(long chatId, String username) {
        render(chatId, username, localization.get("menu.title"));
    }

    /** Used on {@code /start}: the greeting and the main menu are a single message. */
    void showMainMenuWithGreeting(long chatId, String username) {
        String text = localization.get("welcome.greeting", Map.of("username", username))
                + "\n\n" + localization.get("menu.title");
        render(chatId, username, text);
    }

    private void render(long chatId, String username, String text) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        int messageId = gateway.renderText(chatId, previous, text, mainMenuKeyboard());
        chatViewRepository.setState(chatId, ChatState.MENU, messageId, username);
    }

    /** Rendered as a {@link ChatState#MENU} screen too - it's just text with a back button. */
    void showNotImplemented(long chatId, String username) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        InlineKeyboardMarkup keyboard = Keyboards.singleButtonKeyboard(
                localization.get("menu.button.back"), CallbackProtocol.MENU);
        int messageId = gateway.renderText(chatId, previous, localization.get("menu.not-implemented"), keyboard);
        chatViewRepository.setState(chatId, ChatState.MENU, messageId, username);
    }

    private InlineKeyboardMarkup mainMenuKeyboard() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.vote"), CallbackProtocol.MENU_VOTE)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.rating"), CallbackProtocol.MENU_RATING)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.my-tier-list"), CallbackProtocol.MENU_MY_TIER)),
                new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.global-tier-list"), CallbackProtocol.MENU_GLOBAL_TIER))
        )).build();
    }
}
