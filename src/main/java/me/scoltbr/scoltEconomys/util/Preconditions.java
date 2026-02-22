package me.scoltbr.scoltEconomys.util;

public final class Preconditions {
    private Preconditions() {}

    public static void positive(double value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be > 0");
    }

    public static void notNegative(double value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}