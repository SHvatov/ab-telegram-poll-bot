package academy.backend.pollbot.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

/** Shared inline-keyboard building blocks used across the bot's screens. */
final class Keyboards {

    /** Telegram's hard cap on inline button label length. */
    static final int BUTTON_TEXT_MAX_LENGTH = 64;

    private Keyboards() {
    }

    static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    static InlineKeyboardMarkup singleButtonKeyboard(String label, String callbackData) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button(label, callbackData))))
                .build();
    }

    static String truncateButtonText(String text) {
        if (text.length() <= BUTTON_TEXT_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, BUTTON_TEXT_MAX_LENGTH - 1) + "…";
    }
}
