package academy.backend.pollbot.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/** The text and keyboard for one of the bot's text-based screens. */
record ScreenContent(String text, InlineKeyboardMarkup keyboard) {
}
