package academy.backend.pollbot;

import academy.backend.pollbot.config.MemesConfig;
import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.config.i18n.LocalizationLoader;
import academy.backend.pollbot.core.concurrency.ChatSequencer;
import academy.backend.pollbot.domain.MemeDefinition;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.domain.Rating;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.RateLimitRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.telegram.api.PollBotUpdateConsumer;
import academy.backend.pollbot.telegram.routing.BotService;
import academy.backend.pollbot.telegram.routing.CallbackProtocol;
import academy.backend.pollbot.tierlist.TierListGenerator;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.RedisClient;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * Drives the real update-processing pipeline (PollBotUpdateConsumer -> BotService -> Flow classes
 * -> repositories) against a real Redis, simulating two users whose events land interleaved in
 * the same {@code consume()} batch - the exact scenario that makes per-chat ordering matter. The
 * Telegram side is a mock: this test is about the app's own state handling, not the Bot API.
 */
@Testcontainers
class PollBotIT {

    private static final String MEME_CODE = "test_meme";
    private static final long ALICE_ID = 1001L;
    private static final long BOB_ID = 1002L;

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(6379);

    @Test
    void interleavedEventsFromMultipleUsersAreProcessedCorrectlyAndInIsolation() throws Exception {
        RedisClient redisClient = RedisClient.create(REDIS.getHost(), REDIS.getMappedPort(6379));
        try {
            MemeDefinition meme = new MemeDefinition(1, MEME_CODE, "Test meme", "memes/mem_pro_kotika.jpg",
                    "Test description", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
            MemeManager memeManager = new MemeManager(new MemesConfig(List.of(meme)), Clock.systemUTC());
            String memeToken = MemeManager.token(meme);
            Localization localization = new Localization(new LocalizationLoader().load());

            long ttlSeconds = Duration.ofDays(7).toSeconds();
            UserRepository userRepository = new UserRepository(redisClient, ttlSeconds);
            VoteRepository voteRepository = new VoteRepository(redisClient, ttlSeconds);
            ChatViewRepository chatViewRepository = new ChatViewRepository(redisClient);
            RateLimitRepository rateLimitRepository = new RateLimitRepository(redisClient);

            TelegramClient telegramClient = fakeTelegramClient();
            ChatSequencer chatSequencer = new ChatSequencer();
            TierListGenerator tierListGenerator = new TierListGenerator();
            BotService botService = BotService.create(
                    telegramClient, memeManager, localization, userRepository, voteRepository, chatViewRepository,
                    rateLimitRepository, tierListGenerator, chatSequencer);
            PollBotUpdateConsumer updateConsumer = new PollBotUpdateConsumer(chatSequencer, botService);

            // Both users start in the same poll batch.
            updateConsumer.consume(List.of(
                    startUpdate(1, ALICE_ID),
                    startUpdate(2, BOB_ID)));
            awaitTrue(() -> redisClient.exists("user:" + ALICE_ID) && redisClient.exists("user:" + BOB_ID));

            // Each user's own three-step click sequence (open the list, open the meme, vote) has to
            // stay in order per chat, even though every step from both users is submitted together.
            updateConsumer.consume(List.of(
                    callbackUpdate(3, ALICE_ID, CallbackProtocol.SHOW_VOTE_LIST),
                    callbackUpdate(4, BOB_ID, CallbackProtocol.SHOW_VOTE_LIST),
                    callbackUpdate(5, ALICE_ID, CallbackProtocol.openMemeForVote(memeToken)),
                    callbackUpdate(6, BOB_ID, CallbackProtocol.openMemeForVote(memeToken)),
                    callbackUpdate(7, ALICE_ID, CallbackProtocol.submitVote(memeToken, Rating.S)),
                    callbackUpdate(8, BOB_ID, CallbackProtocol.submitVote(memeToken, Rating.A))));

            String aliceVotesKey = "user:" + ALICE_ID + ":votes";
            String bobVotesKey = "user:" + BOB_ID + ":votes";
            awaitTrue(() -> "S".equals(redisClient.hget(aliceVotesKey, MEME_CODE))
                    && "A".equals(redisClient.hget(bobVotesKey, MEME_CODE)));

            // Each user's vote landed under their own key, undisturbed by the other user's events
            // interleaved in the same batch.
            assertEquals("S", redisClient.hget(aliceVotesKey, MEME_CODE));
            assertEquals("A", redisClient.hget(bobVotesKey, MEME_CODE));

            Map<String, String> ratingCounts = redisClient.hgetAll("meme:" + MEME_CODE + ":rating");
            assertEquals("1", ratingCounts.get("S"));
            assertEquals("1", ratingCounts.get("A"));
        } finally {
            redisClient.close();
        }
    }

    private static TelegramClient fakeTelegramClient() {
        AtomicInteger messageIdSeq = new AtomicInteger(1);
        return mock(TelegramClient.class, invocation -> {
            Object method = invocation.getArgument(0);
            if (method instanceof SendMessage || method instanceof SendPhoto) {
                return Message.builder().messageId(messageIdSeq.getAndIncrement()).build();
            }
            return null;
        });
    }

    private static Update startUpdate(int updateId, long userId) {
        Message message = Message.builder()
                .messageId(1)
                .chat(Chat.builder().id(userId).type("private").build())
                .from(testUser(userId))
                .text("/start")
                .date((int) (System.currentTimeMillis() / 1000))
                .build();
        Update update = new Update();
        update.setUpdateId(updateId);
        update.setMessage(message);
        return update;
    }

    private static Update callbackUpdate(int updateId, long userId, String data) {
        Message contextMessage = Message.builder()
                .messageId(1)
                .chat(Chat.builder().id(userId).type("private").build())
                .build();
        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId("cbq-" + updateId);
        callbackQuery.setFrom(testUser(userId));
        callbackQuery.setData(data);
        callbackQuery.setMessage(contextMessage);
        Update update = new Update();
        update.setUpdateId(updateId);
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    private static User testUser(long userId) {
        return User.builder().id(userId).firstName("Test").isBot(false).build();
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Condition not met within timeout");
    }
}
