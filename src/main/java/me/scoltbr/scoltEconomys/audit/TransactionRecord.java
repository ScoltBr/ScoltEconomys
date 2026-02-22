package me.scoltbr.scoltEconomys.audit;

import java.time.Instant;
import java.util.UUID;

public record TransactionRecord(
        Instant at,
        TransactionType type,
        UUID from,
        UUID to,
        double gross,
        double net,
        double fee,
        String source,
        String note
) {}