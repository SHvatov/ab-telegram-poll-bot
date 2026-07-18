package academy.backend.pollbot.telegram.api;

import academy.backend.pollbot.redis.CurrentChatState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
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
        executeSafely(method, chatId, "edit caption of message " + messageId);
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
        return executeSafely(method, chatId, "send message").getMessageId();
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
        // The stream must stay open for the whole upload, so execute() has to happen inside this
        // try-with-resources rather than after it.
        try (in) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            SendPhoto method = SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(in, fileName))
                    .caption(caption)
                    .replyMarkup(keyboard)
                    .build();
            Message sent;
            try {
                sent = telegramClient.execute(method);
            } catch (TelegramApiException e) {
                throw new BotOperationException("Failed to send photo to chat " + chatId, e);
            }
            return sent.getMessageId();
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

    /**
     * Execute-around: every {@link BotApiMethod} call needs the same "wrap failures into
     * {@link BotOperationException}" handling, so the try/catch lives here once. Note this can't
     * cover {@link SendPhoto} - its multipart upload puts it under {@code PartialBotApiMethod}
     * rather than {@code BotApiMethod} in the library's type hierarchy, so {@link #sendPhoto}
     * handles its own call.
     */
    private <T extends Serializable, M extends BotApiMethod<T>> T executeSafely(M method, long chatId, String actionText) {
        try {
            return telegramClient.execute(method);
        } catch (TelegramApiException e) {
            throw new BotOperationException("Failed to " + actionText + " for chat " + chatId, e);
        }
    }

    /**
     * Telegram's Bot API doesn't expose a distinct error code for "message is not modified" -
     * every 4xx validation failure shares {@code error_code: 400}, and the description text is
     * the only way to tell them apart. This checks the structured fields Telegram does give us
     * ({@code error_code} and {@code description}) rather than pattern-matching the fully
     * assembled {@link TelegramApiException#getMessage()} string.
     */
    private static boolean isNotModified(TelegramApiException e) {
        if (!(e instanceof TelegramApiRequestException requestException)) {
            return false;
        }
        if (!Integer.valueOf(400).equals(requestException.getErrorCode())) {
            return false;
        }
        String description = requestException.getApiResponse();
        return description != null && description.toLowerCase(Locale.ROOT).contains("message is not modified");
    }
}
