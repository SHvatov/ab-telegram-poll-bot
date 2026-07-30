package academy.backend.pollbot;

import academy.backend.pollbot.config.AppConfig;
import academy.backend.pollbot.config.AppConfigLoader;
import academy.backend.pollbot.config.MemesConfig;
import academy.backend.pollbot.config.MemesConfigLoader;
import academy.backend.pollbot.config.i18n.Localization;
import academy.backend.pollbot.config.i18n.LocalizationLoader;
import academy.backend.pollbot.core.concurrency.ChatSequencer;
import academy.backend.pollbot.domain.MemeManager;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.RateLimitRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.scheduler.IdleSessionScheduler;
import academy.backend.pollbot.scheduler.ListRefreshScheduler;
import academy.backend.pollbot.telegram.api.PollBotUpdateConsumer;
import academy.backend.pollbot.telegram.routing.BotService;
import academy.backend.pollbot.tierlist.TierListGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import redis.clients.jedis.RedisClient;

import java.time.Clock;
import java.time.Duration;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        // Tier-list images are rendered with Java2D; keep AWT off any real display.
        System.setProperty("java.awt.headless", "true");

        AppConfig appConfig = new AppConfigLoader().load();
        MemesConfig memesConfig = new MemesConfigLoader().load();
        MemeManager memeManager = new MemeManager(memesConfig, Clock.systemUTC());
        Localization localization = new Localization(new LocalizationLoader().load());

        RedisClient redisClient = RedisClient.create(
                appConfig.redis().host(), Integer.parseInt(appConfig.redis().port()));
        long ttlSeconds = Duration.ofDays(appConfig.redis().ttlDays()).toSeconds();
        UserRepository userRepository = new UserRepository(redisClient, ttlSeconds);
        VoteRepository voteRepository = new VoteRepository(redisClient, ttlSeconds);
        ChatViewRepository chatViewRepository = new ChatViewRepository(redisClient);
        RateLimitRepository rateLimitRepository = new RateLimitRepository(redisClient);

        ChatSequencer chatSequencer = new ChatSequencer();
        TierListGenerator tierListGenerator = new TierListGenerator();

        TelegramClient telegramClient = new OkHttpTelegramClient(appConfig.bot().token());
        BotService botService = BotService.create(
                telegramClient, memeManager, localization, userRepository, voteRepository, chatViewRepository,
                rateLimitRepository, tierListGenerator, chatSequencer);

        PollBotUpdateConsumer updateConsumer = new PollBotUpdateConsumer(chatSequencer, botService);

        ListRefreshScheduler scheduler = new ListRefreshScheduler(
                chatSequencer, chatViewRepository, botService, appConfig.scheduler().refreshIntervalSeconds());
        IdleSessionScheduler idleScheduler = new IdleSessionScheduler(chatSequencer, chatViewRepository, botService);

        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(appConfig.bot().token(), updateConsumer);
        scheduler.start();
        idleScheduler.start();
        log.info("Poll bot started with {} meme(s) configured", memeManager.count());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down poll bot...");
            scheduler.stop();
            idleScheduler.stop();
            try {
                botsApplication.close();
            } catch (Exception e) {
                log.warn("Error while closing Telegram bots application", e);
            }
            chatSequencer.shutdown();
            tierListGenerator.shutdown();
            redisClient.close();
        }, "shutdown-hook"));
    }

    private Main() {
    }
}
