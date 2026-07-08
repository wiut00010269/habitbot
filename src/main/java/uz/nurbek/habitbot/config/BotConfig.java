package uz.nurbek.habitbot.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.nurbek.habitbot.bot.TelegramBotService;

/**
 * Registers the bot with Telegram using the new (v10) TelegramBotsLongPollingApplication.
 * The old TelegramBotsApi/DefaultBotSession classes no longer exist in this version.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BotConfig {

    private final TelegramBotService telegramBotService;
    private TelegramBotsLongPollingApplication botsApplication;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBot() {
        try {
            botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(telegramBotService.getBotToken(), telegramBotService);
            log.info("Telegram bot registered and polling started.");
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (botsApplication != null) {
            botsApplication.close();
        }
    }

}