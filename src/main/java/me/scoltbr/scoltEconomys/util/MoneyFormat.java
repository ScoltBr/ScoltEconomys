package me.scoltbr.scoltEconomys.util;

import java.text.DecimalFormat;

public final class MoneyFormat {

    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.##");

    private MoneyFormat() {}

    public static String format(double value) {

        double abs = Math.abs(value);

        if (abs >= 1_000_000_000_000_000d)
            return formatCompact(value, 1_000_000_000_000_000d, "Q");

        if (abs >= 1_000_000_000_000d)
            return formatCompact(value, 1_000_000_000_000d, "T");

        if (abs >= 1_000_000_000d)
            return formatCompact(value, 1_000_000_000d, "B");

        if (abs >= 1_000_000d)
            return formatCompact(value, 1_000_000d, "M");

        if (abs >= 1_000d)
            return formatCompact(value, 1_000d, "K");

        return FORMAT.format(value);
    }

    private static String formatCompact(double value, double divisor, String suffix) {
        double scaled = value / divisor;
        return new DecimalFormat("#,##0.##").format(scaled) + suffix;
    }
}