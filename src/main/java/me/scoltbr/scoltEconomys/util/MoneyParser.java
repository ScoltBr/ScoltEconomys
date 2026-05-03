package me.scoltbr.scoltEconomys.util;

import java.math.BigDecimal;
import java.util.Locale;

public final class MoneyParser {

    private MoneyParser() {}

    private static final String[] SUFFIXES = {
        "", "K", "M", "B", "T", "Q", "QQ", "SX", "SP", "O", "N", "D", "UN"
    };

    public static BigDecimal parse(String input) {
        if (input == null || input.isBlank())
            throw new IllegalArgumentException("Valor vazio");

        input = input.trim().toUpperCase(Locale.ROOT).replace(",", "");

        BigDecimal multiplier = BigDecimal.ONE;

        // Tenta encontrar o sufixo mais longo que combine (ex: "UN" vs "U" se existisse)
        for (int i = SUFFIXES.length - 1; i >= 1; i--) {
            String s = SUFFIXES[i];
            if (!s.isEmpty() && input.endsWith(s)) {
                multiplier = BigDecimal.valueOf(1000).pow(i);
                input = input.substring(0, input.length() - s.length());
                break;
            }
        }

        try {
            BigDecimal value = new BigDecimal(input);
            return value.multiply(multiplier);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: " + input);
        }
    }

}