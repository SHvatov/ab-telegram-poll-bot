package academy.backend.pollbot.telegram.ui;

import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.domain.Rating;
import academy.backend.pollbot.i18n.Localization;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.routing.CallbackProtocol;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Picking a meme to vote on, casting a vote, and seeing your own vote for it. */
public final class VoteFlow {

    private final TelegramGateway gateway;
    private final Localization localization;
    private final MemeManager memeManager;
    private final VoteRepository voteRepository;
    private final ChatViewRepository chatViewRepository;

    public VoteFlow(TelegramGateway gateway, Localization localization, MemeManager memeManager,
                     VoteRepository voteRepository, ChatViewRepository chatViewRepository) {
        this.gateway = gateway;
        this.localization = localization;
        this.memeManager = memeManager;
        this.voteRepository = voteRepository;
        this.chatViewRepository = chatViewRepository;
    }

    public void showList(long chatId, String username) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        ScreenContent content = buildListContent(username);
        int messageId = gateway.renderText(chatId, previous, content.text(), content.keyboard());
        chatViewRepository.setState(chatId, ChatState.CHOOSING_MEME, messageId, username);
    }

    /** Invoked by the background scheduler to refresh an already-open list in place. */
    public void refreshList(CurrentChatState state) {
        ScreenContent content = buildListContent(state.username());
        gateway.renderText(state.chatId(), state, content.text(), content.keyboard());
    }

    public void openMeme(long chatId, String username, String memeCode) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        Optional<MemeDefinition> memeOpt = memeManager.findByCode(memeCode);
        if (memeOpt.isEmpty()) {
            showError(chatId, previous, username, localization.get("error.meme-not-found"));
            return;
        }
        MemeDefinition meme = memeOpt.get();
        Optional<Rating> userVote = voteRepository.getUserVote(username, memeCode);

        String caption;
        InlineKeyboardMarkup keyboard;
        if (userVote.isPresent()) {
            caption = alreadyVotedCaption(meme, userVote.get());
            keyboard = backButtonKeyboard();
        } else {
            caption = localization.get("vote.detail.caption", Map.of("description", meme.description()));
            keyboard = rateButtonsKeyboard(memeCode);
        }
        int messageId = gateway.renderPhoto(chatId, previous, meme.path(), caption, keyboard);
        chatViewRepository.setState(chatId, ChatState.RATING_MEME, messageId, username);
    }

    /** @return the alert text to show the user via answerCallbackQuery */
    public String submitVote(long chatId, String username, String memeCode, Rating rating) {
        Optional<MemeDefinition> memeOpt = memeManager.findByCode(memeCode);
        if (memeOpt.isEmpty()) {
            return localization.get("error.meme-not-found");
        }
        MemeDefinition meme = memeOpt.get();
        if (!memeManager.isAvailable(meme, OffsetDateTime.now())) {
            return localization.get("vote.unavailable-alert");
        }
        if (!voteRepository.saveVoteIfAbsent(username, memeCode, rating)) {
            return localization.get("vote.already-voted-alert");
        }

        String caption = alreadyVotedCaption(meme, rating);
        InlineKeyboardMarkup keyboard = backButtonKeyboard();

        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        if (previous != null && previous.state() == ChatState.RATING_MEME) {
            gateway.updatePhotoCaption(chatId, previous.messageId(), caption, keyboard);
            chatViewRepository.setState(chatId, ChatState.RATING_MEME, previous.messageId(), username);
        } else {
            int messageId = gateway.renderPhoto(chatId, previous, meme.path(), caption, keyboard);
            chatViewRepository.setState(chatId, ChatState.RATING_MEME, messageId, username);
        }
        return localization.get("vote.saved-alert");
    }

    private void showError(long chatId, CurrentChatState previous, String username, String message) {
        InlineKeyboardMarkup keyboard = Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MENU);
        int messageId = gateway.renderText(chatId, previous, message, keyboard);
        chatViewRepository.setState(chatId, ChatState.MENU, messageId, username);
    }

    private ScreenContent buildListContent(String username) {
        List<MemeDefinition> memes = memeManager.availableAsOf(OffsetDateTime.now());
        if (memes.isEmpty()) {
            return new ScreenContent(localization.get("vote.list.empty"), backToMenuKeyboard());
        }
        Map<String, Rating> userVotes = voteRepository.getUserVotes(username);
        return new ScreenContent(localization.get("vote.list.title"), listKeyboard(memes, userVotes));
    }

    private InlineKeyboardMarkup listKeyboard(List<MemeDefinition> memes, Map<String, Rating> userVotes) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (MemeDefinition meme : memes) {
            String label = itemLabel(meme, Optional.ofNullable(userVotes.get(meme.code())));
            rows.add(new InlineKeyboardRow(Keyboards.button(label, CallbackProtocol.voteOpen(meme.code()))));
        }
        rows.add(new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.back"), CallbackProtocol.MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup rateButtonsKeyboard(String memeCode) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Rating rating : Rating.values()) {
            String label = localization.get("vote.rate.button." + rating.name().toLowerCase());
            rows.add(new InlineKeyboardRow(Keyboards.button(label, CallbackProtocol.voteRate(memeCode, rating))));
        }
        rows.add(new InlineKeyboardRow(Keyboards.button(localization.get("vote.rate.button.back"), CallbackProtocol.VOTE_BACK)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private InlineKeyboardMarkup backButtonKeyboard() {
        return Keyboards.singleButtonKeyboard(localization.get("vote.rate.button.back"), CallbackProtocol.VOTE_BACK);
    }

    private InlineKeyboardMarkup backToMenuKeyboard() {
        return Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MENU);
    }

    private String alreadyVotedCaption(MemeDefinition meme, Rating rating) {
        return localization.get("vote.detail.already-voted",
                Map.of("description", meme.description(), "rating", rating.name()));
    }

    private String itemLabel(MemeDefinition meme, Optional<Rating> userVote) {
        String globalRatingText = voteRepository.getGlobalRating(meme.code())
                .map(Enum::name).orElse(localization.get("rating.none"));
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
        return Keyboards.truncateButtonText(label);
    }
}
