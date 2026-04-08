package me.scoltbr.scoltEconomys.util;

import java.util.Locale;

public final class MoneyParser {

    private MoneyParser() {}

    private static final String[] SUFFIXES = {
        "", "K", "M", "B", "T", "Q", "QQ", "SX", "SP", "O", "N", "D", "UN"
    };

    public static double parse(String input) {
        if (input == null || input.isBlank())
            throw new IllegalArgumentException("Valor vazio");

        input = input.trim().toUpperCase(Locale.ROOT).replace(",", "");

        double multiplier = 1.0;
        String suffix = "";

        // Tenta encontrar o sufixo mais longo que combine (ex: "UN" vs "U" se existisse)
        for (int i = SUFFIXES.length - 1; i >= 1; i--) {
            String s = SUFFIXES[i];
            if (!s.isEmpty() && input.endsWith(s)) {
                multiplier = Math.pow(1000, i);
                input = input.substring(0, input.length() - s.length());
                break;
            }
        }

        try {
            double value = Double.parseDouble(input);
            return value * multiplier;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: " + input);
        }
    }

}