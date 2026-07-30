package academy.backend.pollbot.telegram.routing;

import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.core.concurrency.ChatSequencer;
import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.RateLimitRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.ui.MenuFlow;
import academy.backend.pollbot.telegram.ui.RatingFlow;
import academy.backend.pollbot.telegram.ui.TierListFlow;
import academy.backend.pollbot.telegram.ui.VoteFlow;
import academy.backend.pollbot.tierlist.TierListGenerator;
import academy.backend.pollbot.tierlist.TierListImageRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

public final class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    private final TelegramGateway gateway;
    private final UserRepository userRepository;
    private final Localization localization;
    private final MenuFlow menuFlow;
    private final VoteFlow voteFlow;
    private final RatingFlow ratingFlow;
    private final TierListFlow tierListFlow;

    private BotService(TelegramGateway gateway, UserRepository userRepository, Localization localization,
                        MenuFlow menuFlow, VoteFlow voteFlow, RatingFlow ratingFlow, TierListFlow tierListFlow) {
        this.gateway = gateway;
        this.userRepository = userRepository;
        this.localization = localization;
        this.menuFlow = menuFlow;
        this.voteFlow = voteFlow;
        this.ratingFlow = ratingFlow;
        this.tierListFlow = tierListFlow;
    }

    public static BotService create(TelegramClient telegramClient, MemeManager memeManager, Localization localization,
                                     UserRepository userRepository, VoteRepository voteRepository,
                                     ChatViewRepository chatViewRepository, RateLimitRepository rateLimitRepository,
                                     TierListGenerator tierListGenerator, ChatSequencer chatSequencer) {
        TelegramGateway gateway = new TelegramGateway(telegramClient);
        MenuFlow menuFlow = new MenuFlow(gateway, localization, chatViewRepository);
        VoteFlow voteFlow = new VoteFlow(gateway, localization, memeManager, voteRepository, chatViewRepository);
        RatingFlow ratingFlow = new RatingFlow(gateway, localization, memeManager, voteRepository, chatViewRepository);
        TierListFlow tierListFlow = new TierListFlow(gateway, localization, memeManager, voteRepository,
                chatViewRepository, rateLimitRepository, tierListGenerator, new TierListImageRenderer(), chatSequencer);
        return new BotService(gateway, userRepository, localization, menuFlow, voteFlow, ratingFlow, tierListFlow);
    }

    public void handleStart(Message message) {
        long userId = message.getFrom().getId();
        userRepository.registerIfAbsent(userId);
        menuFlow.showMainMenuWithGreeting(message.getChatId(), userId);
    }

    /**
     * Any message that isn't a recognized command. The bot is button-driven, so it removes the
     * user's stray message and re-posts the menu as a fresh message.
     */
    public void handleUnknownMessage(Message message) {
        long userId = message.getFrom().getId();
        userRepository.registerIfAbsent(userId);
        menuFlow.showUnknownCommandFresh(message.getChatId(), userId, message.getMessageId());
    }

    /** Bounces an idle session back to the main menu (invoked by the idle-session sweeper). */
    public void resetToMenu(CurrentChatState state) {
        menuFlow.showMainMenu(state.chatId(), state.userId());
    }

    public void handleCallback(CallbackQuery query) {
        long userId = query.getFrom().getId();
        userRepository.registerIfAbsent(userId);
        long chatId = query.getMessage().getChatId();
        String data = query.getData();
        String alertText;

        try {
            alertText = dispatch(chatId, userId, data);
        } catch (RuntimeException e) {
            log.error("Failed to handle callback '{}' for chat {}", data, chatId, e);
            alertText = localization.get("error.generic");
        }

        gateway.answerCallbackQuery(query.getId(), alertText);
    }

    private String dispatch(long chatId, long userId, String data) {
        Optional<String> voteOpen = CallbackProtocol.parseOpenMemeForVote(data);
        Optional<String> ratingOpen = CallbackProtocol.parseOpenMemeForRating(data);
        Optional<CallbackProtocol.SubmitVote> submitVote = CallbackProtocol.parseSubmitVote(data);

        if (CallbackProtocol.MAIN_MENU.equals(data)) {
            menuFlow.showMainMenu(chatId, userId);
        } else if (CallbackProtocol.SHOW_VOTE_LIST.equals(data)) {
            voteFlow.showList(chatId, userId);
        } else if (CallbackProtocol.SHOW_RATING_LIST.equals(data)) {
            ratingFlow.showList(chatId, userId);
        } else if (CallbackProtocol.MY_TIER_LIST.equals(data)) {
            return tierListFlow.request(chatId, userId, true);
        } else if (CallbackProtocol.GLOBAL_TIER_LIST.equals(data)) {
            return tierListFlow.request(chatId, userId, false);
        } else if (CallbackProtocol.SOURCE.equals(data)) {
            menuFlow.showSource(chatId, userId);
        } else if (CallbackProtocol.VOTE_LIST_BACK.equals(data)) {
            voteFlow.showList(chatId, userId);
        } else if (CallbackProtocol.RATING_LIST_BACK.equals(data)) {
            ratingFlow.showList(chatId, userId);
        } else if (voteOpen.isPresent()) {
            voteFlow.openMeme(chatId, userId, voteOpen.get());
        } else if (ratingOpen.isPresent()) {
            ratingFlow.openMeme(chatId, userId, ratingOpen.get());
        } else if (submitVote.isPresent()) {
            return voteFlow.submitVote(chatId, userId, submitVote.get().memeToken(), submitVote.get().rating());
        } else {
            log.warn("Unrecognized callback data for chat {}: {}", chatId, data);
            menuFlow.showUnknownCommand(chatId, userId);
            return localization.get("error.unknown-command");
        }
        return null;
    }

    public void refreshList(CurrentChatState state) {
        if (state.state() == ChatState.CHOOSING_MEME) {
            voteFlow.refreshList(state);
        } else if (state.state() == ChatState.WATCHING_MEME_RATINGS) {
            ratingFlow.refreshList(state);
        }
    }
}
