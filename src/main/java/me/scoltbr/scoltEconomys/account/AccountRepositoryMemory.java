package me.scoltbr.scoltEconomys.account;

import java.math.BigDecimal;

import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AccountRepositoryMemory implements AccountRepository {

    private final Map<UUID, PlayerAccount> store = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public AccountRepositoryMemory(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<PlayerAccount> load(UUID uuid) {
        return Optional.ofNullable(store.get(uuid))
                .map(a -> new PlayerAccount(a.uuid(), a.wallet(), a.bank(), a.lastUpdate()));
    }

    @Override
    public void upsertBatch(List<PlayerAccount> accounts) {
        for (PlayerAccount a : accounts) {
            store.put(a.uuid(), new PlayerAccount(a.uuid(), a.wallet(), a.bank(), Instant.now()));
        }
        // só pra ver funcionando no console
        plugin.getLogger().fine("Saved batch accounts=" + accounts.size());
    }

    @Override
    public List<TopBalanceRow> topTotal(int limit) {
        return store.values().stream()
                .map(acc -> new TopBalanceRow(acc.uuid(), null, acc.wallet().add(acc.bank())))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .limit(limit)
                .toList();
    }

    @Override
    public void updatePlayerName(UUID uuid, String name) {
        // sem persistência em memória — no-op
    }

    @Override
    public Optional<BigDecimal> getWalletBalanceSync(UUID uuid) {
        if (!store.containsKey(uuid)) return Optional.empty();
        return Optional.of(store.get(uuid).wallet());
    }

    @Override
    public boolean addWalletBalanceSync(UUID uuid, BigDecimal amount) {
        if (!store.containsKey(uuid)) return false;
        BigDecimal current = store.get(uuid).wallet();
        if (current.add(amount).compareTo(BigDecimal.ZERO) < 0) return false;
        store.get(uuid).setWallet(current.add(amount));
        return true;
    }

    @Override
    public GlobalEconomyData getGlobalEconomyData() {
        BigDecimal w = store.values().stream().map(PlayerAccount::wallet).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal b = store.values().stream().map(PlayerAccount::bank).reduce(BigDecimal.ZERO, BigDecimal::add);
        int c = store.size();
        
        int topCount = Math.max(1, c / 10);
        BigDecimal t10 = store.values().stream()
                .map(acc -> acc.wallet().add(acc.bank()))
                .sorted(Comparator.reverseOrder())
                .limit(topCount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        return new GlobalEconomyData(w, b, c, t10);
    }
}