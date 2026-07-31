package academy.backend.pollbot.telegram.ui;

import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.domain.ChatState;
import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.redis.CurrentChatState;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.TelegramGateway;
import academy.backend.pollbot.telegram.routing.CallbackProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Admin-only actions, available exclusively to {@link #ADMIN_USER_ID}: picking a contest winner
 * and showing usage statistics. The buttons are hidden from everyone else (see {@link MenuFlow}),
 * and the dispatcher re-checks {@link #isAdmin(long)} before invoking anything here.
 */
public final class AdminFlow {

    public static final long ADMIN_USER_ID = 753224886L;

    private static final int[] PERCENTILES = {50, 75, 90, 95, 99};

    private static final Logger log = LoggerFactory.getLogger(AdminFlow.class);

    private final TelegramGateway gateway;
    private final Localization localization;
    private final MemeManager memeManager;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final ChatViewRepository chatViewRepository;

    public AdminFlow(TelegramGateway gateway, Localization localization, MemeManager memeManager,
                     UserRepository userRepository, VoteRepository voteRepository,
                     ChatViewRepository chatViewRepository) {
        this.gateway = gateway;
        this.localization = localization;
        this.memeManager = memeManager;
        this.userRepository = userRepository;
        this.voteRepository = voteRepository;
        this.chatViewRepository = chatViewRepository;
    }

    public static boolean isAdmin(long userId) {
        return userId == ADMIN_USER_ID;
    }

    /**
     * Picks a random winner among users who voted for at least one meme and direct-messages them a
     * congratulation. Returns the alert text to show the admin.
     */
    public String pickWinner() {
        List<Long> userIds = userRepository.allUserIds();
        List<Long> voteCounts = voteRepository.votedMemeCounts(userIds);
        List<Long> eligible = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            if (voteCounts.get(i) > 0) {
                eligible.add(userIds.get(i));
            }
        }
        if (eligible.isEmpty()) {
            return localization.get("admin.winner.empty-alert");
        }

        long winnerId = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        try {
            gateway.sendNotification(winnerId, localization.get("admin.winner.announcement"));
        } catch (RuntimeException e) {
            log.error("Picked winner {} but failed to notify them", winnerId, e);
            return localization.get("admin.winner.notify-failed-alert", Map.of("userId", winnerId));
        }
        log.info("Contest winner picked: {}", winnerId);
        return localization.get("admin.winner.picked-alert", Map.of("userId", winnerId));
    }

    public void showStats(long chatId, long userId) {
        CurrentChatState previous = chatViewRepository.getState(chatId).orElse(null);
        InlineKeyboardMarkup keyboard =
                Keyboards.singleButtonKeyboard(localization.get("menu.button.back"), CallbackProtocol.MAIN_MENU);
        int messageId = gateway.renderText(chatId, previous, buildStats(), keyboard);
        setState(chatId, ChatState.MENU, messageId, userId);
    }

    private String buildStats() {
        long userCount = userRepository.count();
        List<Long> userIds = userRepository.allUserIds();
        List<Long> voteCounts = voteRepository.votedMemeCounts(userIds);
        long voters = voteCounts.stream().filter(count -> count > 0).count();

        StringBuilder sb = new StringBuilder();
        sb.append(localization.get("admin.stats.title")).append("\n\n");
        sb.append(localization.get("admin.stats.users", Map.of("count", userCount))).append("\n");
        sb.append(localization.get("admin.stats.voters", Map.of("count", voters))).append("\n\n");

        sb.append(localization.get("admin.stats.meme-votes-header")).append("\n");
        for (MemeDefinition meme : memeManager.all()) {
            sb.append("  • ").append(meme.name()).append(" — ").append(voteRepository.voterCount(meme.code())).append("\n");
        }

        if (voters == 0) {
            sb.append("\n").append(localization.get("admin.stats.empty"));
            return sb.toString();
        }

        List<Long> sorted = new ArrayList<>(voteCounts);
        sorted.sort(Long::compareTo);
        sb.append("\n").append(localization.get("admin.stats.percentiles-header")).append("\n");
        for (int p : PERCENTILES) {
            sb.append(localization.get("admin.stats.percentile-line",
                    Map.of("p", p, "value", percentile(sorted, p)))).append("\n");
        }

        long maxVoted = voteCounts.stream().mapToLong(Long::longValue).max().orElse(0);
        sb.append("\n").append(localization.get("admin.stats.histogram-header")).append("\n");
        for (long threshold = 1; threshold <= maxVoted; threshold++) {
            long atLeast = voteCounts.stream().filter(count -> count >= threshold).count();
            sb.append(localization.get("admin.stats.histogram-line",
                    Map.of("n", threshold, "count", atLeast))).append("\n");
        }
        return sb.toString();
    }

    // Nearest-rank percentile over the ascending-sorted per-user vote counts (whole population).
    private static long percentile(List<Long> sortedAsc, int p) {
        if (sortedAsc.isEmpty()) {
            return 0;
        }
        int rank = (int) Math.ceil(p / 100.0 * sortedAsc.size());
        int index = Math.min(Math.max(rank - 1, 0), sortedAsc.size() - 1);
        return sortedAsc.get(index);
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
