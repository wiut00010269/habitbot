package uz.nurbek.habitbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.nurbek.habitbot.bot.TelegramBotService;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BotConfig {

    private final TelegramBotService telegramBotService;

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBotService);
            log.info("Telegram bot registered and polling started.");
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot", e);
        }
    }
}
