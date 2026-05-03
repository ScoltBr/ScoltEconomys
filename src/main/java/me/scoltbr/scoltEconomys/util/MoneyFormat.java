package me.scoltbr.scoltEconomys.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyFormat {

    // Sempre Locale.US para nunca misturar separador de milhar "." com o suffix (ex: "1.000M")
    private static final DecimalFormatSymbols SYMBOLS = new DecimalFormatSymbols(Locale.US);
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##", SYMBOLS);

    private MoneyFormat() {}

    private static final String[] SUFFIXES = {
        "", "K", "M", "B", "T", "Q", "QQ", "SX", "SP", "O", "N", "D", "UN"
    };

    public static String format(java.math.BigDecimal value) {
        if (value == null) return "NaN";
        if (value.compareTo(java.math.BigDecimal.ZERO) == 0) return "0";

        java.math.BigDecimal abs = value.abs();
        int index = 0;
        java.math.BigDecimal scaled = abs;

        java.math.BigDecimal thousand = java.math.BigDecimal.valueOf(1000);
        while (scaled.compareTo(thousand) >= 0 && index < SUFFIXES.length - 1) {
            scaled = scaled.divide(thousand, 2, java.math.RoundingMode.HALF_UP);
            index++;
        }

        // Se o arredondamento resultar em 1000 (ex: 999.999...), promovemos para a próxima unidade
        String formatted = FORMAT.format(scaled);
        if (formatted.equals("1,000") && index < SUFFIXES.length - 1) {
            index++;
            formatted = "1";
        }

        return (value.compareTo(java.math.BigDecimal.ZERO) < 0 ? "-" : "") + formatted + SUFFIXES[index];
    }

}