package me.scoltbr.scoltEconomys.util;

import java.util.UUID;

public final class LockOrder {
    private LockOrder() {}

    public static UUID first(UUID a, UUID b) {
        return compare(a, b) <= 0 ? a : b;
    }

    public static UUID second(UUID a, UUID b) {
        return compare(a, b) <= 0 ? b : a;
    }

    private static int compare(UUID a, UUID b) {
        int c1 = Long.compare(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (c1 != 0) return c1;
        return Long.compare(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}