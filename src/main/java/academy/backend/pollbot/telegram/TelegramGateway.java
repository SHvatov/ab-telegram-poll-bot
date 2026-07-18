package academy.backend.pollbot.telegram;

import academy.backend.pollbot.redis.CurrentChatState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * All direct Telegram Bot API calls used to render the bot's screens, plus the edit-vs-resend
 * policy: text states are edited in place, but Telegram cannot convert a message between text and
 * photo via an edit, so transitions to/from a photo state always delete the old message and send
 * a new one (see {@link academy.backend.pollbot.domain.ChatState}).
 */
public final class TelegramGateway {

    private static final Logger log = LoggerFactory.getLogger(TelegramGateway.class);

    private final TelegramClient telegramClient;

    public TelegramGateway(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    /** Renders a text screen, editing the previous message in place when possible. */
    public int renderText(long chatId, CurrentChatState previous, String text, InlineKeyboardMarkup keyboard) {
        if (previous != null && previous.state().isText()) {
            editText(chatId, previous.messageId(), text, keyboard);
            return previous.messageId();
        }
        if (previous != null) {
            deleteQuietly(chatId, previous.messageId());
        }
        return sendText(chatId, text, keyboard);
    }

    /** Renders a photo screen. Always deletes the previous message and sends a new one. */
    public int renderPhoto(long chatId, CurrentChatState previous, String resourcePath, String caption,
                            InlineKeyboardMarkup keyboard) {
        if (previous != null) {
            deleteQuietly(chatId, previous.messageId());
        }
        return sendPhoto(chatId, resourcePath, caption, keyboard);
    }

    /** Updates only the caption/keyboard of an already-open photo screen, without resending it. */
    public void updatePhotoCaption(long chatId, int messageId, String caption, InlineKeyboardMarkup keyboard) {
        EditMessageCaption method = EditMessageCaption.builder()
                .chatId(chatId)
                .messageId(messageId)
                .caption(caption)
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(method);
        } catch (TelegramApiException e) {
            throw new BotOperationException("Failed to edit caption of message " + messageId + " in chat " + chatId, e);
        }
    }

    public void answerCallbackQuery(String callbackQueryId, String alertText) {
        var builder = AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId);
        if (alertText != null) {
            builder.text(alertText);
        }
        try {
            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.warn("Failed to answer callback query {}: {}", callbackQueryId, e.getMessage());
        }
    }

    private int sendText(long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage method = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            return telegramClient.execute(method).getMessageId();
        } catch (TelegramApiException e) {
            throw new BotOperationException("Failed to send message to chat " + chatId, e);
        }
    }

    private void editText(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText method = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(keyboard)
                .build();
        try {
            telegramClient.execute(method);
        } catch (TelegramApiException e) {
            if (!isNotModified(e)) {
                throw new BotOperationException("Failed to edit message " + messageId + " in chat " + chatId, e);
            }
        }
    }

    private int sendPhoto(long chatId, String resourcePath, String caption, InlineKeyboardMarkup keyboard) {
        InputStream in = getClass().getResourceAsStream("/" + resourcePath);
        if (in == null) {
            throw new IllegalStateException("Meme image not found on classpath: " + resourcePath);
        }
        try (in) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            SendPhoto method = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(in, fileName))
                    .caption(caption)
                    .replyMarkup(keyboard)
                    .build();
            return telegramClient.execute(method).getMessageId();
        } catch (TelegramApiException e) {
            throw new BotOperationException("Failed to send photo to chat " + chatId, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void deleteQuietly(long chatId, int messageId) {
        try {
            telegramClient.execute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build());
        } catch (TelegramApiException e) {
            log.debug("Could not delete message {} in chat {}: {}", messageId, chatId, e.getMessage());
        }
    }

    private static boolean isNotModified(TelegramApiException e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("message is not modified");
    }
}
