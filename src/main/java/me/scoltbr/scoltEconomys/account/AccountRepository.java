package me.scoltbr.scoltEconomys.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Optional<PlayerAccount> load(UUID uuid);
    void upsertBatch(List<PlayerAccount> accounts);
    List<TopBalanceRow> topTotal(int limit);
    void updatePlayerName(UUID uuid, String name);
    Optional<BigDecimal> getWalletBalanceSync(UUID uuid);
    boolean addWalletBalanceSync(UUID uuid, BigDecimal amount);
    GlobalEconomyData getGlobalEconomyData();
}