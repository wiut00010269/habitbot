package uz.nurbek.habitbot.parser;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based parser: no AI, just keyword matching ("if-if" as requested).
 * Splits the message by comma, then for each piece looks for a number and a keyword.
 * <p>
 * Example input:
 * "10 ta turnik, 1700 yo'l kiraga, 9700 qadam kun davomida, kecha 16 ta otjimaniya, 14 minut audio mutolaa"
 * <p>
 * Add new habits by adding new entries to the RULES map below - no code logic changes needed.
 */
@Component
public class HabitParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)");

    // "3h 27m", "3 h 27 m", "3soat 27minut" style screen-time -> converted to total minutes
    private static final Pattern SCREEN_TIME_PATTERN =
            Pattern.compile("(\\d+)\\s*(?:h|soat)\\D{0,3}(\\d+)\\s*(?:m|min|minut)");

    // keyword (lowercase, normalized apostrophe) -> [category, subtype, unit]
    // Order matters: more specific keywords should be listed BEFORE more generic ones.
    private static final Map<String, String[]> RULES = new LinkedHashMap<>();

    static {
        // Exercise
        RULES.put("turnik", new String[]{"EXERCISE", "pullup", "count"});
        RULES.put("otjimaniya", new String[]{"EXERCISE", "pushup", "count"});
        RULES.put("otjimanie", new String[]{"EXERCISE", "pushup", "count"});
        RULES.put("pushup", new String[]{"EXERCISE", "pushup", "count"});
        RULES.put("press", new String[]{"EXERCISE", "abs", "count"});
        RULES.put("dips", new String[]{"EXERCISE", "dips", "count"});
        RULES.put("bike", new String[]{"EXERCISE", "bike", "km"});
        RULES.put("velosiped", new String[]{"EXERCISE", "bike", "km"});
        RULES.put("qadam", new String[]{"STEPS", "steps", "steps"});

        // Habit / discipline tracking
        RULES.put("pray", new String[]{"HABIT", "pray", "count"});
        RULES.put("namaz", new String[]{"HABIT", "pray", "count"});
        RULES.put("ibodat", new String[]{"HABIT", "pray", "count"});
        RULES.put("no soda", new String[]{"HABIT", "no_soda", "day"});
        RULES.put("soda", new String[]{"HABIT", "no_soda", "day"});

        // Study
        RULES.put("leetcode", new String[]{"STUDY", "leetcode", "count"});
        RULES.put("page", new String[]{"READING", "page", "pages"});
        RULES.put("sahifa", new String[]{"READING", "page", "pages"});

        // Reading (time-based)
        RULES.put("audio mutolaa", new String[]{"READING", "audio", "minutes"});
        RULES.put("audio kitob", new String[]{"READING", "audio", "minutes"});
        RULES.put("mutolaa", new String[]{"READING", "book", "minutes"});
        RULES.put("kitob o'qi", new String[]{"READING", "book", "minutes"});

        // Screen time
        RULES.put("screen time", new String[]{"SCREEN_TIME", "screen_time", "minutes"});
        RULES.put("ekran vaqti", new String[]{"SCREEN_TIME", "screen_time", "minutes"});

        // Expense (checked after exercise/reading/habit keywords so more specific ones win)
        RULES.put("yo'l kiraga", new String[]{"EXPENSE", "transport", "som"});
        RULES.put("yol kiraga", new String[]{"EXPENSE", "transport", "som"});
        RULES.put("kiraga", new String[]{"EXPENSE", "transport", "som"});
        RULES.put("metro", new String[]{"EXPENSE", "transport", "som"});
        RULES.put("taxi", new String[]{"EXPENSE", "transport", "som"});
        RULES.put("taksi", new String[]{"EXPENSE", "transport", "som"});
        RULES.put("produkta", new String[]{"EXPENSE", "food", "som"});
        RULES.put("produxta", new String[]{"EXPENSE", "food", "som"});
        RULES.put("ovqat", new String[]{"EXPENSE", "food", "som"});
        RULES.put("abet", new String[]{"EXPENSE", "food", "som"});
        RULES.put("food", new String[]{"EXPENSE", "food", "som"});
        RULES.put("nonushta", new String[]{"EXPENSE", "food", "som"});
        RULES.put("tushlik", new String[]{"EXPENSE", "food", "som"});
        RULES.put("kechki", new String[]{"EXPENSE", "food", "som"});
        RULES.put("so'm", new String[]{"EXPENSE", "other", "som"});
        RULES.put("som", new String[]{"EXPENSE", "other", "som"});
        RULES.put("kredit", new String[]{"EXPENSE", "loan", "som"});
    }

    /**
     * Parses a single free-text message into zero or more entries.
     * Splits by comma; each comma-separated chunk should contain exactly one number (or one
     * screen-time pattern) + one keyword.
     */
    public List<ParsedEntry> parse(String rawMessage) {
        List<ParsedEntry> result = new ArrayList<>();
        if (rawMessage == null || rawMessage.isBlank()) {
            return result;
        }

        String[] chunks = rawMessage.split(",");
        for (String chunk : chunks) {
            ParsedEntry entry = parseChunk(chunk);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private ParsedEntry parseChunk(String chunk) {
        String normalized = normalize(chunk);
        int dayOffset = normalized.contains("kecha") ? -1 : 0;

        // Screen time special case first: has TWO numbers in one chunk ("3h 27m"),
        // must be handled before the generic single-number search below.
        Matcher screenTime = SCREEN_TIME_PATTERN.matcher(normalized);
        if (screenTime.find()) {
            int hours = Integer.parseInt(screenTime.group(1));
            int minutes = Integer.parseInt(screenTime.group(2));
            BigDecimal totalMinutes = BigDecimal.valueOf(hours * 60L + minutes);
            return new ParsedEntry("SCREEN_TIME", "screen_time", totalMinutes, "minutes", dayOffset);
        }

        // 1. find the number
        Matcher m = NUMBER_PATTERN.matcher(normalized);
        if (!m.find()) {
            return null; // no number found -> skip this chunk, can't record a habit without a value
        }
        BigDecimal value = new BigDecimal(m.group(1).replace(",", "."));

        // 2. find first matching keyword rule (LinkedHashMap preserves priority order)
        for (Map.Entry<String, String[]> rule : RULES.entrySet()) {
            if (normalized.contains(rule.getKey())) {
                String[] meta = rule.getValue();
                return new ParsedEntry(meta[0], meta[1], value, meta[2], dayOffset);
            }
        }

        // 3. no keyword matched -> store as CUSTOM/unspecified so nothing gets silently dropped
        return new ParsedEntry("CUSTOM", "unspecified", value, null, dayOffset);
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("\u2019", "'")   // curly apostrophe -> straight
                .replace("\u02BC", "'")
                .trim();
    }

}