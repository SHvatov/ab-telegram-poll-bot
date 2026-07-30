package academy.backend.pollbot.telegram.ui;

import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.core.concurrency.ChatSequencer;
import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.domain.Rating;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.RateLimitRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.routing.CallbackProtocol;
import academy.backend.pollbot.tierlist.TierList;
import academy.backend.pollbot.tierlist.TierListGenerator;
import academy.backend.pollbot.tierlist.TierListImageRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class TierListFlow {

    private static final Logger log = LoggerFactory.getLogger(TierListFlow.class);

    // One tier-list generation per user per 5 minutes (enforced in Redis, so it holds across restarts).
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60;

    private final TelegramGateway gateway;
    private final Localization localization;
    private final MemeManager memeManager;
    private final VoteRepository voteRepository;
    private final ChatViewRepository chatViewRepository;
    private final RateLimitRepository rateLimitRepository;
    private final TierListGenerator generator;
    private final TierListImageRenderer renderer;
    private final ChatSequencer chatSequencer;

    public TierListFlow(TelegramGateway gateway, Localization localization, MemeManager memeManager,
                        VoteRepository voteRepository, ChatViewRepository chatViewRepository,
                        RateLimitRepository rateLimitRepository, TierListGenerator generator,
                        TierListImageRenderer renderer, ChatSequencer chatSequencer) {
        this.gateway = gateway;
        this.localization = localization;
        this.memeManager = memeManager;
        this.voteRepository = voteRepository;
        this.chatViewRepository = chatViewRepository;
        this.rateLimitRepository = rateLimitRepository;
        this.generator = generator;
        this.renderer = renderer;
        this.chatSequencer = chatSequencer;
    }

    /**
     * Handles a tier-list request. Runs the cheap Redis reads inline (to answer "nothing to show"
     * or "you're rate-limited" immediately), then hands the heavy image rendering to the generator
     * pool. Returns the callback-alert text to show the user right away.
     */
    public String request(long chatId, long userId, boolean personal) {
        TierList tierList = new TierList(personal ? buildPersonalTiers(userId) : buildGlobalTiers());
        if (tierList.isEmpty()) {
            return localization.get("tier.empty");
        }

        String rateLimitKey = rateLimitKey(userId);
        long retryAfter = rateLimitRepository.acquire(rateLimitKey, RATE_LIMIT_WINDOW_SECONDS);
        if (retryAfter > 0) {
            return localization.get("tier.rate-limited", Map.of("seconds", retryAfter));
        }

        String caption = localization.get(personal ? "tier.caption.personal" : "tier.caption.global");
        boolean submitted = generator.submit(() -> generateAndSend(chatId, userId, tierList, caption));
        if (!submitted) {
            // Couldn't queue the work - refund the rate-limit slot so the user may retry right away.
            rateLimitRepository.release(rateLimitKey);
            return localization.get("tier.queue-full");
        }
        return localization.get("tier.generating");
    }

    private void generateAndSend(long chatId, long userId, TierList tierList, String caption) {
        byte[] image;
        try {
            image = renderer.render(tierList);
        } catch (RuntimeException e) {
            log.error("Failed to render tier list for chat {}", chatId, e);
            chatSequencer.execute(chatId, () -> sendFailure(chatId, userId));
            return;
        }
        // Hand the actual send back to the chat's sequencer so it stays ordered with the user's
        // other actions (and never races the view state that image rendering doesn't touch).
        chatSequencer.execute(chatId, () -> sendTierList(chatId, userId, image, caption));
    }

    private void sendTierList(long chatId, long userId, byte[] image, String caption) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        InlineKeyboardMarkup keyboard =
                Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU);
        int messageId = gateway.renderPhotoBytes(chatId, previous, image, "tierlist.png", caption, keyboard);
        setState(chatId, ChatState.VIEWING_TIER_LIST, messageId, userId);
    }

    private void sendFailure(long chatId, long userId) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        InlineKeyboardMarkup keyboard =
                Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU);
        int messageId = gateway.renderText(chatId, previous, localization.get("tier.failed"), keyboard);
        setState(chatId, ChatState.MENU, messageId, userId);
    }

    private Map<Rating, List<MemeDefinition>> buildPersonalTiers(long userId) {
        Map<String, Rating> votes = voteRepository.getUserVotes(userId);
        Map<Rating, List<MemeDefinition>> tiers = emptyTiers();
        for (MemeDefinition meme : memeManager.all()) {
            Rating rating = votes.get(meme.code());
            if (rating != null) {
                tiers.get(rating).add(meme);
            }
        }
        return tiers;
    }

    private Map<Rating, List<MemeDefinition>> buildGlobalTiers() {
        Map<Rating, List<MemeDefinition>> tiers = emptyTiers();
        for (MemeDefinition meme : memeManager.all()) {
            voteRepository.getGlobalRating(meme.code()).ifPresent(rating -> tiers.get(rating).add(meme));
        }
        return tiers;
    }

    private static Map<Rating, List<MemeDefinition>> emptyTiers() {
        Map<Rating, List<MemeDefinition>> tiers = new EnumMap<>(Rating.class);
        for (Rating rating : Rating.values()) {
            tiers.put(rating, new ArrayList<>());
        }
        return tiers;
    }

    private static String rateLimitKey(long userId) {
        return "user:" + userId + ":tierlist:rl";
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
