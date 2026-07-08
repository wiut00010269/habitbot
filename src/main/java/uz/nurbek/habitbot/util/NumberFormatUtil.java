package uz.nurbek.habitbot.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberFormatUtil {

    private static final DecimalFormat MONEY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        MONEY_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private NumberFormatUtil() {
    }

    public static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public static String money(BigDecimal value) {
        return MONEY_FORMAT.format(value.setScale(0, RoundingMode.HALF_UP));
    }

    public static String format(BigDecimal value, String unit) {
        if ("som".equalsIgnoreCase(unit)) {
            return money(value);
        }
        return plain(value);
    }
}
