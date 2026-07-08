package uz.nurbek.habitbot.parser;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ParsedEntry {
    private final String category;   // EXERCISE, STEPS, EXPENSE, READING, CUSTOM
    private final String subtype;    // pullup, pushup, transport, audio, book, steps, other
    private final BigDecimal value;
    private final String unit;       // count, som, steps, minutes
    private final int dayOffset;     // 0 = today, -1 = yesterday
}
