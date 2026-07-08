package uz.nurbek.habitbot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.nurbek.habitbot.entity.HabitEntry;
import uz.nurbek.habitbot.parser.HabitParser;
import uz.nurbek.habitbot.parser.ParsedEntry;
import uz.nurbek.habitbot.repository.HabitEntryRepository;
import uz.nurbek.habitbot.service.ReportService;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TelegramBotService extends TelegramLongPollingBot {

    private final String botUsername;
    private final HabitParser parser;
    private final HabitEntryRepository repository;
    private final ReportService reportService;
    private final Set<Long> allowedChatIds;

    public TelegramBotService(@Value("${telegram.bot.token}") String botToken,
                               @Value("${telegram.bot.username}") String botUsername,
                               @Value("${allowed.chat-ids:}") String allowedChatIdsRaw,
                               HabitParser parser,
                               HabitEntryRepository repository,
                               ReportService reportService) {
        super(botToken);
        this.botUsername = botUsername;
        this.parser = parser;
        this.repository = repository;
        this.reportService = reportService;
        this.allowedChatIds = Arrays.stream(allowedChatIdsRaw.split(","))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        if (!allowedChatIds.isEmpty() && !allowedChatIds.contains(chatId)) {
            log.warn("Ignoring message from unauthorized chatId={}", chatId);
            return;
        }

        String text = update.getMessage().getText().trim();

        if (text.startsWith("/")) {
            handleCommand(chatId, text);
            return;
        }

        handleHabitMessage(chatId, text);
    }

    private void handleCommand(Long chatId, String command) {
        switch (command) {
            case "/start" -> sendText(chatId, """
                    Salom! Odat va harajatlaringizni shu formatda yozing:
                    "10 ta turnik, 1700 yo'l kiraga, 9700 qadam"

                    Buyruqlar:
                    /kunlik - bugungi statistika
                    /haftalik - shu haftalik statistika
                    /oylik - shu oylik statistika
                    """);
            case "/kunlik" -> reportService.sendDailyReport(chatId, LocalDate.now());
            case "/haftalik" -> reportService.sendWeeklyReport(chatId, LocalDate.now());
            case "/oylik" -> reportService.sendMonthlyReport(chatId, LocalDate.now());
            default -> sendText(chatId, "Noma'lum buyruq. /start ni bosing.");
        }
    }

    private void handleHabitMessage(Long chatId, String text) {
        List<ParsedEntry> parsed = parser.parse(text);

        if (parsed.isEmpty()) {
            sendText(chatId, "Hech narsa tushunmadim \uD83D\uDE15 Masalan: \"10 ta turnik, 1700 yo'l kiraga\" deb yozib ko'ring.");
            return;
        }

        StringBuilder confirmation = new StringBuilder("Qayd qilindi:\n");
        for (ParsedEntry p : parsed) {
            LocalDate date = LocalDate.now().plusDays(p.getDayOffset());
            HabitEntry entity = HabitEntry.builder()
                    .chatId(chatId)
                    .rawText(text)
                    .category(p.getCategory())
                    .subtype(p.getSubtype())
                    .value(p.getValue())
                    .unit(p.getUnit())
                    .entryDate(date)
                    .build();
            repository.save(entity);

            confirmation.append("✅ ")
                    .append(p.getSubtype()).append(": ")
                    .append(p.getValue())
                    .append(p.getUnit() != null ? " " + p.getUnit() : "")
                    .append(p.getDayOffset() == -1 ? " (kecha)" : "")
                    .append("\n");
        }

        sendText(chatId, confirmation.toString());
    }

    public void sendText(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chatId={}", chatId, e);
        }
    }

    public void sendPhoto(Long chatId, byte[] pngBytes, String caption) {
        try {
            InputFile inputFile = new InputFile(new ByteArrayInputStream(pngBytes), "chart.png");
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(inputFile);
            sendPhoto.setCaption(caption);
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            log.error("Failed to send photo to chatId={}", chatId, e);
        }
    }
}
