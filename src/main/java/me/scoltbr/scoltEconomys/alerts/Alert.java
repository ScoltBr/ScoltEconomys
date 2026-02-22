package me.scoltbr.scoltEconomys.alerts;

import java.time.Instant;

public record Alert(
        AlertType type,
        String message,
        Instant since
) {}
