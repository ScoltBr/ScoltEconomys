package me.scoltbr.scoltEconomys.util;

import java.util.Locale;

public final class MoneyParser {

    private MoneyParser() {}

    public static double parse(String input) {
        if (input == null || input.isBlank())
            throw new IllegalArgumentException("Valor vazio");

        input = input.trim().toUpperCase(Locale.ROOT).replace(",", "");

        double multiplier = 1.0;

        if (input.endsWith("K")) {
            multiplier = 1_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("M")) {
            multiplier = 1_000_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("B")) {
            multiplier = 1_000_000_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("T")) {
            multiplier = 1_000_000_000_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("UN")) {
            multiplier = 1_000_000_000_000_000_000_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 2);
        } else if (input.endsWith("D")) {
            multiplier = 1_000_000_000_000_000_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("N")) {
            multiplier = 1_000_000_000_000_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("O")) {
            multiplier = 1_000_000_000_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("SP")) {
            multiplier = 1_000_000_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 2);
        } else if (input.endsWith("SX")) {
            multiplier = 1_000_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 2);
        } else if (input.endsWith("QQ")) {
            multiplier = 1_000_000_000_000_000_000d;
            input = input.substring(0, input.length() - 2);
        } else if (input.endsWith("Q")) {
            multiplier = 1_000_000_000_000_000d;
            input = input.substring(0, input.length() - 1);
        }

        double value = Double.parseDouble(input);
        return value * multiplier;
    }
}