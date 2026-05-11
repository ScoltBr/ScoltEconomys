package me.scoltbr.scoltEconomys.util;

import java.math.BigDecimal;
import java.util.List;

public final class SparklineUtil {

    private static final String[] BARS = {" ", "▂", "▃", "▄", "▅", "▆", "▇", "█"};

    private SparklineUtil() {}

    /**
     * Gera uma string de sparkline Unicode para uma lista de valores.
     */
    public static String generate(List<BigDecimal> values) {
        if (values == null || values.size() < 2) return "";

        double min = values.stream().mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(1);
        double range = max - min;

        StringBuilder sb = new StringBuilder();
        for (BigDecimal val : values) {
            if (range == 0) {
                sb.append(BARS[4]); // Meio termo se for linha reta
                continue;
            }
            int index = (int) (((val.doubleValue() - min) / range) * (BARS.length - 1));
            sb.append(BARS[index]);
        }
        return sb.toString();
    }
}
