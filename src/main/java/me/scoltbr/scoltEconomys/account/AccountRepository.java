package me.scoltbr.scoltEconomys.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Optional<PlayerAccount> load(UUID uuid);
    void upsertBatch(List<PlayerAccount> accounts);
    List<TopBalanceRow> topTotal(int limit);
    void updatePlayerName(UUID uuid, String name);
    java.util.OptionalDouble getWalletBalanceSync(UUID uuid);
    boolean addWalletBalanceSync(UUID uuid, double amount);
    GlobalEconomyData getGlobalEconomyData();
}