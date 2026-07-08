package uz.nurbek.habitbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.nurbek.habitbot.bot.TelegramBotService;
import uz.nurbek.habitbot.repository.HabitEntryRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReportService {

    private final HabitEntryRepository repository;
    private final ChartService chartService;
    private final TelegramBotService telegramBotService;

    // @Lazy breaks the circular dependency: TelegramBotService -> ReportService -> TelegramBotService
    public ReportService(HabitEntryRepository repository,
                          ChartService chartService,
                          @Lazy TelegramBotService telegramBotService) {
        this.repository = repository;
        this.chartService = chartService;
        this.telegramBotService = telegramBotService;
    }

    // ---------- Manual triggers (used by /kunlik /haftalik /oylik commands) ----------

    public void sendDailyReport(Long chatId, LocalDate date) {
        sendReport(chatId, "Kunlik hisobot (" + date + ")", date, date);
    }

    public void sendWeeklyReport(Long chatId, LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = anyDayInWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        sendReport(chatId, "Haftalik hisobot (" + monday + " - " + sunday + ")", monday, sunday);
    }

    public void sendMonthlyReport(Long chatId, LocalDate anyDayInMonth) {
        LocalDate first = anyDayInMonth.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate last = anyDayInMonth.with(TemporalAdjusters.lastDayOfMonth());
        sendReport(chatId, "Oylik hisobot (" + first + " - " + last + ")", first, last);
    }

    private void sendReport(Long chatId, String title, LocalDate from, LocalDate to) {
        List<HabitEntryRepository.CategoryTotal> totals = repository.aggregateByRange(chatId, from, to);

        if (totals.isEmpty()) {
            telegramBotService.sendText(chatId, title + "\n\nBu davrda hech qanday yozuv topilmadi.");
            return;
        }

        StringBuilder text = new StringBuilder(title).append("\n\n");
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (HabitEntryRepository.CategoryTotal t : totals) {
            String unit = t.getUnit() != null ? t.getUnit() : "";
            text.append("• ").append(t.getSubtype()).append(": ")
                    .append(t.getTotal()).append(" ").append(unit).append("\n");
            labels.add(t.getSubtype());
            values.add(t.getTotal().doubleValue());
        }

        telegramBotService.sendText(chatId, text.toString());

        byte[] chart = chartService.buildBarChart(title, labels, values);
        if (chart != null) {
            telegramBotService.sendPhoto(chatId, chart, title);
        }
    }

    // ---------- Scheduled automatic reports ----------

    // Every day at 23:00 - daily report for every user who has ever sent data
    @Scheduled(cron = "0 0 23 * * *")
    public void scheduledDaily() {
        LocalDate today = LocalDate.now();
        for (Long chatId : repository.findDistinctChatIds()) {
            sendDailyReport(chatId, today);
        }
    }

    // Every Sunday at 20:00 - weekly report
    @Scheduled(cron = "0 0 20 * * SUN")
    public void scheduledWeekly() {
        LocalDate today = LocalDate.now();
        for (Long chatId : repository.findDistinctChatIds()) {
            sendWeeklyReport(chatId, today);
        }
    }

    // Checked daily at 22:30 - fires only when today is the last day of the month
    @Scheduled(cron = "0 30 22 * * *")
    public void scheduledMonthlyCheck() {
        LocalDate today = LocalDate.now();
        if (!today.equals(today.with(TemporalAdjusters.lastDayOfMonth()))) {
            return;
        }
        for (Long chatId : repository.findDistinctChatIds()) {
            sendMonthlyReport(chatId, today);
        }
    }
}
