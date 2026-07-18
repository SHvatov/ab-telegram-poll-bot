package academy.backend.pollbot;

import academy.backend.pollbot.config.AppConfig;
import academy.backend.pollbot.config.AppConfigLoader;
import academy.backend.pollbot.config.MemesConfig;
import academy.backend.pollbot.config.MemesConfigLoader;
import academy.backend.pollbot.i18n.Localization;
import academy.backend.pollbot.repository.ChatViewRepository;
import academy.backend.pollbot.repository.UserRepository;
import academy.backend.pollbot.repository.VoteRepository;
import academy.backend.pollbot.scheduler.ListRefreshScheduler;
import academy.backend.pollbot.telegram.BotService;
import academy.backend.pollbot.telegram.PollBotUpdateConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import redis.clients.jedis.RedisClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        AppConfig appConfig = new AppConfigLoader().load();
        MemesConfig memesConfig = new MemesConfigLoader().load();
        Localization localization = new Localization();

        RedisClient redisClient = RedisClient.create(appConfig.redisHost(), appConfig.redisPort());
        UserRepository userRepository = new UserRepository(redisClient, appConfig.userDataTtlSeconds());
        VoteRepository voteRepository = new VoteRepository(redisClient, appConfig.userDataTtlSeconds());
        ChatViewRepository chatViewRepository = new ChatViewRepository(redisClient);

        TelegramClient telegramClient = new OkHttpTelegramClient(appConfig.botToken());
        BotService botService = new BotService(telegramClient, memesConfig, localization, userRepository, voteRepository, chatViewRepository);

        // One virtual thread per incoming update, and per background list refresh.
        ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        PollBotUpdateConsumer updateConsumer = new PollBotUpdateConsumer(virtualThreadExecutor, botService);

        ListRefreshScheduler scheduler = new ListRefreshScheduler(
                virtualThreadExecutor, chatViewRepository, botService, appConfig.refreshIntervalSeconds());

        TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
        botsApplication.registerBot(appConfig.botToken(), updateConsumer);
        scheduler.start();
        log.info("Poll bot started with {} meme(s) configured", memesConfig.memes().size());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down poll bot...");
            scheduler.stop();
            try {
                botsApplication.close();
            } catch (Exception e) {
                log.warn("Error while closing Telegram bots application", e);
            }
            virtualThreadExecutor.shutdown();
            redisClient.close();
        }, "shutdown-hook"));
    }

    private Main() {
    }
}
