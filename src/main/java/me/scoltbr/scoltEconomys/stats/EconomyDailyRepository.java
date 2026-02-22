package me.scoltbr.scoltEconomys.stats;

import java.time.LocalDate;
import java.util.Optional;

public interface EconomyDailyRepository {
    void upsert(LocalDate day, EconomySnapshot snapshot);
    Optional<EconomyDailyRow> load(LocalDate day);
}
