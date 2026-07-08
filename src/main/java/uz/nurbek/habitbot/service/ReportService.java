package uz.nurbek.habitbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.nurbek.habitbot.bot.TelegramBotService;
import uz.nurbek.habitbot.entity.HabitEntry;
import uz.nurbek.habitbot.repository.HabitEntryRepository;
import uz.nurbek.habitbot.util.NumberFormatUtil;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
public class ReportService {

    private final HabitEntryRepository repository;
    private final ChartService chartService;
    private final TelegramBotService telegramBotService;
    private final String channelId;

    private static final String[] UZ_MONTHS = {
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    };

    // subtype -> "emoji Label" used in the channel-style report, in fixed display order.
    // Covers every subtype produced by HabitParser. "no_soda" is intentionally excluded here -
    // it gets its own special checkbox line (✅/❌ per day) further down.
    private static final Map<String, String> CHANNEL_EMOJI_LABELS = new LinkedHashMap<>();
    static {
        // Exercise
        CHANNEL_EMOJI_LABELS.put("pullup", "\u23EB Pull up");
        CHANNEL_EMOJI_LABELS.put("pushup", "\u23EC Push up");
        CHANNEL_EMOJI_LABELS.put("abs", "\uD83E\uDDD8 Press");
        CHANNEL_EMOJI_LABELS.put("dips", "\u2195\uFE0F Dips");
        CHANNEL_EMOJI_LABELS.put("bike", "\uD83D\uDEB4 Bike (km)");
        CHANNEL_EMOJI_LABELS.put("steps", "\uD83D\uDC63 Steps");

        // Habit / discipline
        CHANNEL_EMOJI_LABELS.put("pray", "\uD83E\uDD32 Pray");

        // Study
        CHANNEL_EMOJI_LABELS.put("leetcode", "\uD83D\uDFE2 LeetCode");

        // Reading
        CHANNEL_EMOJI_LABELS.put("page", "\uD83D\uDCD7 Page");
        CHANNEL_EMOJI_LABELS.put("audio", "\uD83C\uDFA7 Audio (min)");

        // Screen time
        CHANNEL_EMOJI_LABELS.put("screen_time", "\uD83D\uDCF1 Screen time (min)");

        // Expenses
        CHANNEL_EMOJI_LABELS.put("transport", "\uD83D\uDE95 Transport (so'm)");
        CHANNEL_EMOJI_LABELS.put("food", "\uD83C\uDF7D\uFE0F Food (so'm)");
        CHANNEL_EMOJI_LABELS.put("other", "\uD83D\uDCB5 Other expense (so'm)");

        // Fallback for anything the parser couldn't classify
        CHANNEL_EMOJI_LABELS.put("unspecified", "\u2754 Other");
    }

    private static final Set<String> MONEY_SUBTYPES = Set.of("transport", "food", "other");

    // @Lazy breaks the circular dependency: TelegramBotService -> ReportService -> TelegramBotService
    public ReportService(HabitEntryRepository repository,
                         ChartService chartService,
                         @Lazy TelegramBotService telegramBotService,
                         @Value("${channel.id:}") String channelId) {
        this.repository = repository;
        this.chartService = chartService;
        this.telegramBotService = telegramBotService;
        this.channelId = channelId;
    }

    // ---------- Manual triggers (used by /kunlik /haftalik /oylik commands) ----------

    public void sendDailyReport(Long chatId, LocalDate date) {
        sendReport(chatId, "Kunlik hisobot (" + date + ")", date, date);
    }

    public void sendWeeklyReport(Long chatId, LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = anyDayInWeek.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        sendReport(chatId, "Haftalik hisobot (" + monday + " - " + sunday + ")", monday, sunday);
        broadcastWeeklyToChannel(chatId, monday, sunday);
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
            text.append("\u2022 ").append(t.getSubtype()).append(": ")
                    .append(NumberFormatUtil.format(t.getTotal(), t.getUnit())).append(" ").append(unit).append("\n");
            labels.add(t.getSubtype());
            values.add(t.getTotal().doubleValue());
        }

        telegramBotService.sendText(chatId, text.toString());

        byte[] chart = chartService.buildBarChart(title, labels, values);
        if (chart != null) {
            telegramBotService.sendPhoto(chatId, chart, title);
        }
    }

    public void broadcastWeeklyToChannel(Long chatId, LocalDate monday, LocalDate sunday) {
        if (channelId == null || channelId.isBlank()) {
            return;
        }

        List<HabitEntryRepository.CategoryTotal> totals = repository.aggregateByRange(chatId, monday, sunday);
        Map<String, BigDecimal> totalsBySubtype = new LinkedHashMap<>();
        for (HabitEntryRepository.CategoryTotal t : totals) {
            totalsBySubtype.put(t.getSubtype(), t.getTotal());
        }

        List<HabitEntry> weekEntries =
                repository.findByChatIdAndEntryDateBetweenOrderByEntryDateAsc(chatId, monday, sunday);

        StringBuilder text = new StringBuilder(buildWeekTitle(monday, sunday)).append("\n");

        for (Map.Entry<String, String> entry : CHANNEL_EMOJI_LABELS.entrySet()) {
            BigDecimal total = totalsBySubtype.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            String formatted = MONEY_SUBTYPES.contains(entry.getKey())
                    ? NumberFormatUtil.money(total)
                    : NumberFormatUtil.plain(total);
            text.append(entry.getValue()).append(" : ").append(formatted).append("\n");
        }

        text.append("\uD83E\uDD10 No soda: ").append(buildNoSodaCheckboxes(weekEntries, monday)).append("\n");
        text.append("#weeklyStatistics");

        telegramBotService.sendText(channelId, text.toString());
    }

    private String buildWeekTitle(LocalDate monday, LocalDate sunday) {
        int year = sunday.getYear();
        if (monday.getMonth() == sunday.getMonth()) {
            String month = UZ_MONTHS[monday.getMonthValue() - 1];
            return monday.getDayOfMonth() + "-" + sunday.getDayOfMonth() + "-" + month + ". " + year + "-yil";
        }
        String startMonth = UZ_MONTHS[monday.getMonthValue() - 1];
        String endMonth = UZ_MONTHS[sunday.getMonthValue() - 1];
        return monday.getDayOfMonth() + "-" + sunday.getDayOfMonth() + "-" + startMonth + "-" + endMonth + ". " + year + "-yil";
    }

    private String buildNoSodaCheckboxes(List<HabitEntry> weekEntries, LocalDate monday) {
        StringBuilder box = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            boolean success = weekEntries.stream()
                    .anyMatch(e -> "no_soda".equals(e.getSubtype())
                            && e.getEntryDate().equals(day)
                            && e.getValue().compareTo(BigDecimal.ZERO) > 0);
            box.append(success ? "\u2705" : "\u274C");
        }
        return box.toString();
    }

    private String stripTrailingZero(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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

    // Every Sunday at 20:00 - weekly report (also posts to channel if configured)
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