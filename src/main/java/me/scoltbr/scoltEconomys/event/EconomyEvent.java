package me.scoltbr.scoltEconomys.event;

import java.util.Map;

public record EconomyEvent(
        String id,
        String displayName,
        double interestMultiplier,
        double taxMultiplier,
        long durationSeconds,
        Map<String, Double> sectorBoosts   // sector -> price change modifier per tick
) {}

