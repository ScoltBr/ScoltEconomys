package me.scoltbr.scoltEconomys.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionRecord(
        Instant at,
        TransactionType type,
        UUID from,
        UUID to,
        BigDecimal gross,
        BigDecimal net,
        BigDecimal fee,
        String source,
        String note
) {}