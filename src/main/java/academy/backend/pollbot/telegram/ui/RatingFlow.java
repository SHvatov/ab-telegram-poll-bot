package academy.backend.pollbot.telegram.ui;

import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.routing.CallbackProtocol;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RatingFlow {

    private final TelegramGateway gateway;
    private final Localization localization;
    private final MemeManager memeManager;
    private final VoteRepository voteRepository;
    private final ChatViewRepository chatViewRepository;

    public RatingFlow(TelegramGateway gateway, Localization localization, MemeManager memeManager,
                       VoteRepository voteRepository, ChatViewRepository chatViewRepository) {
        this.gateway = gateway;
        this.localization = localization;
        this.memeManager = memeManager;
        this.voteRepository = voteRepository;
        this.chatViewRepository = chatViewRepository;
    }

    public void showList(long chatId, long userId) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        ScreenContent content = buildListContent();
        int messageId = gateway.renderText(chatId, previous, content.text(), content.keyboard());
        setState(chatId, ChatState.WATCHING_MEME_RATINGS, messageId, userId);
    }

    public void refreshList(CurrentChatState state) {
        ScreenContent content = buildListContent();
        gateway.renderText(state.chatId(), state, content.text(), content.keyboard());
    }

    public void openMeme(long chatId, long userId, String memeToken) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        Optional<MemeDefinition> memeOpt = memeManager.findByToken(memeToken);
        if (memeOpt.isEmpty()) {
            InlineKeyboardMarkup keyboard = Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU);
            int messageId = gateway.renderText(chatId, previous, localization.get("error.meme-not-found"), keyboard);
            setState(chatId, ChatState.MENU, messageId, userId);
            return;
        }
        MemeDefinition meme = memeOpt.get();
        String globalRatingText = voteRepository.getGlobalRating(meme.code()).map(Enum::name).orElse(localization.get("rating.none"));
        String caption = localization.get("rating.detail.caption",
                Map.of("description", meme.description(), "globalRating", globalRatingText));
        InlineKeyboardMarkup keyboard = Keyboards.singleButtonKeyboard(localization.get("vote.rate.button.back"), CallbackProtocol.RATING_LIST_BACK);

        int messageId = gateway.renderPhoto(chatId, previous, meme.path(), caption, keyboard);
        setState(chatId, ChatState.WATCHING_MEME_RATING, messageId, userId);
    }

    private ScreenContent buildListContent() {
        List<MemeDefinition> memes = memeManager.availableAsOf();
        if (memes.isEmpty()) {
            InlineKeyboardMarkup backToMenu = Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU);
            return new ScreenContent(localization.get("rating.list.empty"), backToMenu);
        }
        return new ScreenContent(localization.get("rating.list.title"), listKeyboard(memes));
    }

    private InlineKeyboardMarkup listKeyboard(List<MemeDefinition> memes) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (MemeDefinition meme : memes) {
            rows.add(new InlineKeyboardRow(Keyboards.button(itemLabel(meme), CallbackProtocol.openMemeForRating(MemeManager.token(meme)))));
        }
        rows.add(new InlineKeyboardRow(Keyboards.button(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU)));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private String itemLabel(MemeDefinition meme) {
        String globalRatingText = voteRepository.getGlobalRating(meme.code()).map(Enum::name).orElse(localization.get("rating.none"));
        String label = localization.get("rating.list.item", Map.of(
                "position", meme.position(),
                "description", meme.description(),
                "globalRating", globalRatingText));
        return Keyboards.truncateButtonText(label);
    }

    private void setState(long chatId, ChatState state, int messageId, long userId) {
        chatViewRepository.setState(chatId, state, messageId, userId);
        if (state.isRefreshable()) {
            chatViewRepository.markAsRefreshable(chatId);
        } else {
            chatViewRepository.unmarkAsRefreshable(chatId);
        }
    }
}
