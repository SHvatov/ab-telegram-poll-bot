package academy.backend.pollbot.telegram;

import academy.backend.pollbot.config.MemeDefinition;
import academy.backend.pollbot.config.MemesConfig;
import academy.backend.pollbot.domain.ChatViewType;
import academy.backend.pollbot.domain.Rating;
import academy.backend.pollbot.i18n.Localization;
import academy.backend.pollbot.redis.ChatViewState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates every user-visible flow: greeting/registration, the main menu, the vote and
 * rating lists, meme detail screens, and voting itself.
 * <p>
 * Navigation always edits the chat's single evolving bot message in place when moving between
 * text views (menu/list/not-implemented), but deletes-and-resends when crossing to/from the
 * photo-based meme detail view, because Telegram does not allow converting a message between
 * text and photo via an edit.
 */
public final class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);
    private static final int BUTTON_TEXT_MAX_LENGTH = 64;

    private final TelegramClient telegramClient;
    private final MemesConfig memesConfig;
    private final Localization localization;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final ChatViewRepository chatViewRepository;

    public BotService(TelegramClient telegramClient,
                       MemesConfig memesConfig,
                       Localization localization,
                       UserRepository userRepository,
                       VoteRepository voteRepository,
                       ChatViewRepository chatViewRepository) {
        this.telegramClient = telegramClient;
        this.memesConfig = memesConfig;
        this.localization = localization;
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
        this.chatViewRepository = chatViewRepository;
    }

    // ==================== Entry points ====================

    public void handleStart(Message message) {
        String username = resolveUsername(message.getFrom());
        userRepository.registerIfAbsent(username);

        long chatId = message.getChatId();
        String text = localization.get("welcome.greeting", Map.of("username", username))
                + "\n\n" + localization.get("menu.title");
        int messageId = sendText(chatId, text, mainMenuKeyboard());
        chatViewRepository.setView(chatId, ChatViewType.MENU, messageId, username);
    }

    public void handleCallback(CallbackQuery query) {
        String username = resolveUsername(query.getFrom());
        userRepository.registerIfAbsent(username);
        long chatId = query.getMessage().getChatId();
        String data = query.getData();
        String alertText = null;

        try {
            Optional<String> voteOpen = CallbackData.parseVoteOpen(data);
            Optional<String> ratingOpen = CallbackData.parseRatingOpen(data);
            Optional<CallbackData.VoteRate> voteRate = CallbackData.parseVoteRate(data);

            if (CallbackData.MENU.equals(data)) {
                showMainMenu(chatId, username);
            } else if (CallbackData.MENU_VOTE.equals(data)) {
                showVoteList(chatId, username);
            } else if (CallbackData.MENU_RATING.equals(data)) {
                showRatingList(chatId, username);
            } else if (CallbackData.MENU_MY_TIER.equals(data) || CallbackData.MENU_GLOBAL_TIER.equals(data)) {
                showNotImplemented(chatId, username);
            } else if (CallbackData.VOTE_BACK.equals(data)) {
                showVoteList(chatId, username);
            } else if (CallbackData.RATING_BACK.equals(data)) {
                showRatingList(chatId, username);
            } else if (voteOpen.isPresent()) {
                openMemeForVote(chatId, username, voteOpen.get());
            } else if (ratingOpen.isPresent()) {
                openMemeForRating(chatId, username, ratingOpen.get());
            } else if (voteRate.isPresent()) {
                alertText = submitVote(chatId, username, voteRate.get().memeCode(), voteRate.get().rating());
            } else {
                log.warn("Unrecognized callback data: {}", data);
            }
        } catch (RuntimeException e) {
            log.error("Failed to handle callback '{}' for chat {}", data, chatId, e);
            alertText = localization.get("error.generic");
        }

        answerCallbackQuery(query.getId(), alertText);
    }

    /** Invoked by the background scheduler to refresh an already-open vote/rating list in place. */
    public void refreshList(ChatViewState state) {
        TextView view = state.type() == ChatViewType.VOTE_LIST
                ? buildVoteListView(state.username())
                : buildRatingListView();
        editText(state.chatId(), state.messageId(), view.text(), view.keyboard());
    }

    // ==================== Views ====================

    private void showMainMenu(long chatId, String username) {
        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        transitionToText(chatId, previous, localization.get("menu.title"), mainMenuKeyboard(), ChatViewType.MENU, username);
    }

    private void showVoteList(long chatId, String username) {
        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        TextView view = buildVoteListView(username);
        transitionToText(chatId, previous, view.text(), view.keyboard(), ChatViewType.VOTE_LIST, username);
    }

    private void showRatingList(long chatId, String username) {
        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        TextView view = buildRatingListView();
        transitionToText(chatId, previous, view.text(), view.keyboard(), ChatViewType.RATING_LIST, username);
    }

    private void showNotImplemented(long chatId, String username) {
        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        transitionToText(chatId, previous, localization.get("menu.not-implemented"), backToMenuKeyboard(), ChatViewType.NOT_IMPLEMENTED, username);
    }

    private void openMemeForVote(long chatId, String username, String memeCode) {
        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        Optional<MemeDefinition> memeOpt = memesConfig.findByCode(memeCode);
        if (memeOpt.isEmpty()) {
            transitionToText(chatId, previous, localization.get("error.meme-not-found"), backToMenuKeyboard(), ChatViewType.NOT_IMPLEMENTED, username);
            return;
        }
        MemeDefinition meme = memeOpt.get();
        Optional<Rating> userVote = voteRepository.getUserVote(username, memeCode);

        String caption;
        InlineKeyboardMarkup keyboard;
        if (userVote.isPresent()) {
            caption = localization.get("vote.detail.already-voted",
                    Map.of("description", meme.description(), "rating", userVote.get().name()));
            keyboard = singleBackButtonKeyboard(localization.get("vote.rate.button.back"), CallbackData.VOTE_BACK);
        } else {
            caption = localization.get("vote.detail.caption", Map.of("description", meme.description()));
            keyboard = voteRatingButtonsKeyboard(memeCode);
        }
        transitionToPhoto(chatId, previous, meme.path(), caption, keyboard, username);
    }

    private void openMemeForRating(long chatId, String username, String memeCode) {
        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        Optional<MemeDefinition> memeOpt = memesConfig.findByCode(memeCode);
        if (memeOpt.isEmpty()) {
            transitionToText(chatId, previous, localization.get("error.meme-not-found"), backToMenuKeyboard(), ChatViewType.NOT_IMPLEMENTED, username);
            return;
        }
        MemeDefinition meme = memeOpt.get();
        String globalRatingText = voteRepository.getGlobalRating(memeCode).map(Enum::name).orElse(localization.get("rating.none"));
        String caption = localization.get("rating.detail.caption",
                Map.of("description", meme.description(), "globalRating", globalRatingText));
        InlineKeyboardMarkup keyboard = singleBackButtonKeyboard(localization.get("vote.rate.button.back"), CallbackData.RATING_BACK);
        transitionToPhoto(chatId, previous, meme.path(), caption, keyboard, username);
    }

    /** @return the alert text to show the user via answerCallbackQuery */
    private String submitVote(long chatId, String username, String memeCode, Rating rating) {
        Optional<MemeDefinition> memeOpt = memesConfig.findByCode(memeCode);
        if (memeOpt.isEmpty()) {
            return localization.get("error.meme-not-found");
        }
        MemeDefinition meme = memeOpt.get();
        if (!meme.isAvailable(OffsetDateTime.now())) {
            return localization.get("vote.unavailable-alert");
        }
        if (!voteRepository.saveVoteIfAbsent(username, memeCode, rating)) {
            return localization.get("vote.already-voted-alert");
        }

        String caption = localization.get("vote.detail.already-voted",
                Map.of("description", meme.description(), "rating", rating.name()));
        InlineKeyboardMarkup keyboard = singleBackButtonKeyboard(localization.get("vote.rate.button.back"), CallbackData.VOTE_BACK);

        ChatViewState previous = chatViewRepository.getView(chatId).orElse(null);
        if (previous != null && previous.type() == ChatViewType.MEME_DETAIL) {
            updatePhotoCaption(chatId, previous.messageId(), caption, keyboard);
            chatViewRepository.setView(chatId, ChatViewType.MEME_DETAIL, previous.messageId(), username);
        } else {
            transitionToPhoto(chatId, previous, meme.path(), caption, keyboard, username);
        }
        return localization.get("vote.saved-alert");
    }

    // ==================== View content builders ====================

    private record TextView(String text, InlineKeyboardMarkup keyboard) {
    }

    private TextView buildVoteListView(String username) {
        List<MemeDefinition> memes = memesConfig.availableAsOf(OffsetDateTime.now());
        if (memes.isEmpty()) {
            return new TextView(localization.get("vote.list.empty"), backToMenuKeyboard());
        }
        Map<String, Rating> userVotes = voteRepository.getUserVotes(username);
        return new TextView(localization.get("vote.list.title"), voteListKeyboard(memes, userVotes));
    }

    private TextView buildRatingListView() {
        List<MemeDefinition> memes = memesConfig.availableAsOf(OffsetDateTime.now());
        if (memes.isEmpty()) {
            return new TextView(localization.get("rating.list.empty"), backToMenuKeyboard());
        }
        return new TextView(localization.get("rating.list.title"), ratingListKeyboard(memes));
    }

    // ==================== Keyboards ====================

    private InlineKeyboardMarkup mainMenuKeyboard() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(button(localization.get("menu.button.vote"), CallbackData.MENU_VOTE)),
                new InlineKeyboardRow(button(localization.get("menu.button.rating"), CallbackData.MENU_RATING)),
                new InlineKeyboardRow(button(localization.get("menu.button.my-tier-list"), CallbackData.MENU_MY_TIER)),
                new InlineKeyboardRow(button(localization.get("menu.button.global-tier-list"), CallbackData.MENU_GLOBAL_TIER))
        )).build();
    }

    private InlineKeyboardMarkup voteListKeyboard(List<MemeDefinition> memes, Map<String, Rating> userVotes) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (MemeDefinition meme : memes) {
            String label = voteListItemLabel(meme, Optional.ofNullable(userVotes.get(meme.code())));
            rows.add(new InlineKeyboardRow(button(label, CallbackData.voteOpen(meme.code()))));
        }
        rows.add(new InlineKeyboardRow(button(localization.get("menu.button.back"), CallbackData.MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup ratingListKeyboard(List<MemeDefinition> memes) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (MemeDefinition meme : memes) {
            rows.add(new InlineKeyboardRow(button(ratingListItemLabel(meme), CallbackData.ratingOpen(meme.code()))));
        }
        rows.add(new InlineKeyboardRow(button(localization.get("menu.button.back"), CallbackData.MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup voteRatingButtonsKeyboard(String memeCode) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Rating rating : Rating.values()) {
            rows.add(new InlineKeyboardRow(button(localization.get(rating.buttonLocalizationKey()), CallbackData.voteRate(memeCode, rating))));
        }
        rows.add(new InlineKeyboardRow(button(localization.get("vote.rate.button.back"), CallbackData.VOTE_BACK)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup backToMenuKeyboard() {
        return singleBackButtonKeyboard(localization.get("menu.button.back"), CallbackData.MENU);
    }

    private static InlineKeyboardMarkup singleBackButtonKeyboard(String label, String callbackData) {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(button(label, callbackData))))
                .build();
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    // ==================== Label formatting ====================

    private String voteListItemLabel(MemeDefinition meme, Optional<Rating> userVote) {
        String globalRatingText = voteRepository.getGlobalRating(meme.code()).map(Enum::name).orElse(localization.get("rating.none"));
        String label = userVote.isPresent()
                ? localization.get("vote.list.item.voted", Map.of(
                        "position", meme.position(),
                        "description", meme.description(),
                        "rating", userVote.get().name(),
                        "globalRating", globalRatingText))
                : localization.get("vote.list.item.unvoted", Map.of(
                        "position", meme.position(),
                        "description", meme.description(),
                        "globalRating", globalRatingText));
        return truncate(label, BUTTON_TEXT_MAX_LENGTH);
    }

    private String ratingListItemLabel(MemeDefinition meme) {
        String globalRatingText = voteRepository.getGlobalRating(meme.code()).map(Enum::name).orElse(localization.get("rating.none"));
        String label = localization.get("rating.list.item", Map.of(
                "position", meme.position(),
                "description", meme.description(),
                "globalRating", globalRatingText));
        return truncate(label, BUTTON_TEXT_MAX_LENGTH);
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }

    // ==================== Message transitions ====================

    private void transitionToText(long chatId, ChatViewState previous, String text, InlineKeyboardMarkup keyboard,
                                   ChatViewType newType, String username) {
        int messageId;
        if (previous != null && previous.type().isTextView()) {
            editText(chatId, previous.messageId(), text, keyboard);
            messageId = previous.messageId();
        } else {
            if (previous != null) {
                deleteQuietly(chatId, previous.messageId());
            }
            messageId = sendText(chatId, text, keyboard);
        }
        chatViewRepository.setView(chatId, newType, messageId, username);
    }

    private void transitionToPhoto(long chatId, ChatViewState previous, String resourcePath, String caption,
                                    InlineKeyboardMarkup keyboard, String username) {
        if (previous != null) {
            deleteQuietly(chatId, previous.messageId());
        }
        int messageId = sendPhoto(chatId, resourcePath, caption, keyboard);
        chatViewRepository.setView(chatId, ChatViewType.MEME_DETAIL, messageId, username);
    }

    // ==================== Telegram API primitives ====================

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

    private void updatePhotoCaption(long chatId, int messageId, String caption, InlineKeyboardMarkup keyboard) {
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

    private void answerCallbackQuery(String callbackQueryId, String alertText) {
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

    private static boolean isNotModified(TelegramApiException e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("message is not modified");
    }

    private static String resolveUsername(User user) {
        String username = user.getUserName();
        return (username == null || username.isBlank()) ? "id_" + user.getId() : username;
    }
}
