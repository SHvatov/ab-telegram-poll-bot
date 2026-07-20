package academy.backend.pollbot.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

record ScreenContent(String text, InlineKeyboardMarkup keyboard) {
}
