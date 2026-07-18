package academy.backend.pollbot.telegram.routing;

import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.i18n.Localization;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.ui.MenuFlow;
import academy.backend.pollbot.telegram.ui.RatingFlow;
import academy.backend.pollbot.telegram.ui.VoteFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

/**
 * Registers users and routes {@code /start} / callback-query updates to the right flow
 * (menu, voting, or read-only ratings). The actual screen rendering lives in
 * {@link MenuFlow}, {@link VoteFlow} and {@link RatingFlow}; this class only dispatches.
 */
public final class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    private final TelegramGateway gateway;
    private final UserRepository userRepository;
    private final Localization localization;
    private final MenuFlow menuFlow;
    private final VoteFlow voteFlow;
    private final RatingFlow ratingFlow;

    private BotService(TelegramGateway gateway, UserRepository userRepository, Localization localization,
                        MenuFlow menuFlow, VoteFlow voteFlow, RatingFlow ratingFlow) {
        this.gateway = gateway;
        this.userRepository = userRepository;
        this.localization = localization;
        this.menuFlow = menuFlow;
        this.voteFlow = voteFlow;
        this.ratingFlow = ratingFlow;
    }

    public static BotService create(TelegramClient telegramClient, MemeManager memeManager, Localization localization,
                                     UserRepository userRepository, VoteRepository voteRepository,
                                     ChatViewRepository chatViewRepository) {
        TelegramGateway gateway = new TelegramGateway(telegramClient);
        MenuFlow menuFlow = new MenuFlow(gateway, localization, chatViewRepository);
        VoteFlow voteFlow = new VoteFlow(gateway, localization, memeManager, voteRepository, chatViewRepository);
        RatingFlow ratingFlow = new RatingFlow(gateway, localization, memeManager, voteRepository, chatViewRepository);
        return new BotService(gateway, userRepository, localization, menuFlow, voteFlow, ratingFlow);
    }

    public void handleStart(Message message) {
        String username = resolveUsername(message.getFrom());
        userRepository.registerIfAbsent(username);
        menuFlow.showMainMenuWithGreeting(message.getChatId(), username);
    }

    public void handleCallback(CallbackQuery query) {
        String username = resolveUsername(query.getFrom());
        userRepository.registerIfAbsent(username);
        long chatId = query.getMessage().getChatId();
        String data = query.getData();
        String alertText = null;

        try {
            Optional<String> voteOpen = CallbackProtocol.parseVoteOpen(data);
            Optional<String> ratingOpen = CallbackProtocol.parseRatingOpen(data);
            Optional<CallbackProtocol.VoteRate> voteRate = CallbackProtocol.parseVoteRate(data);

            if (CallbackProtocol.MENU.equals(data)) {
                menuFlow.showMainMenu(chatId, username);
            } else if (CallbackProtocol.MENU_VOTE.equals(data)) {
                voteFlow.showList(chatId, username);
            } else if (CallbackProtocol.MENU_RATING.equals(data)) {
                ratingFlow.showList(chatId, username);
            } else if (CallbackProtocol.MENU_MY_TIER.equals(data) || CallbackProtocol.MENU_GLOBAL_TIER.equals(data)) {
                menuFlow.showNotImplemented(chatId, username);
            } else if (CallbackProtocol.VOTE_BACK.equals(data)) {
                voteFlow.showList(chatId, username);
            } else if (CallbackProtocol.RATING_BACK.equals(data)) {
                ratingFlow.showList(chatId, username);
            } else if (voteOpen.isPresent()) {
                voteFlow.openMeme(chatId, username, voteOpen.get());
            } else if (ratingOpen.isPresent()) {
                ratingFlow.openMeme(chatId, username, ratingOpen.get());
            } else if (voteRate.isPresent()) {
                alertText = voteFlow.submitVote(chatId, username, voteRate.get().memeCode(), voteRate.get().rating());
            } else {
                log.warn("Unrecognized callback data: {}", data);
            }
        } catch (RuntimeException e) {
            log.error("Failed to handle callback '{}' for chat {}", data, chatId, e);
            alertText = localization.get("error.generic");
        }

        gateway.answerCallbackQuery(query.getId(), alertText);
    }

    /** Invoked by the background scheduler to refresh an already-open vote/rating list in place. */
    public void refreshList(CurrentChatState state) {
        if (state.state() == ChatState.CHOOSING_MEME) {
            voteFlow.refreshList(state);
        } else if (state.state() == ChatState.WATCHING_MEME_RATINGS) {
            ratingFlow.refreshList(state);
        }
    }

    private static String resolveUsername(User user) {
        String username = user.getUserName();
        return (username == null || username.isBlank()) ? "id_" + user.getId() : username;
    }
}
