package me.scoltbr.scoltEconomys.account;

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

        return store.values().stream() // ou seu Map interno
                .map(acc -> new TopBalanceRow(acc.uuid(), acc.wallet() + acc.bank()))
                .sorted((a, b) -> Double.compare(b.total(), a.total()))
                .limit(limit)
                .toList();
    }

    @Override
    public java.util.OptionalDouble getWalletBalanceSync(UUID uuid) {
        if (!store.containsKey(uuid)) return java.util.OptionalDouble.empty();
        return java.util.OptionalDouble.of(store.get(uuid).wallet());
    }

    @Override
    public boolean addWalletBalanceSync(UUID uuid, double amount) {
        if (!store.containsKey(uuid)) return false;
        double current = store.get(uuid).wallet();
        if (current + amount < 0) return false;
        store.get(uuid).setWallet(current + amount);
        return true;
    }

    @Override
    public GlobalEconomyData getGlobalEconomyData() {
        double w = store.values().stream().mapToDouble(PlayerAccount::wallet).sum();
        double b = store.values().stream().mapToDouble(PlayerAccount::bank).sum();
        int c = store.size();
        
        int topCount = Math.max(1, c / 10);
        double t10 = store.values().stream()
                .mapToDouble(acc -> acc.wallet() + acc.bank())
                .boxed()
                .sorted(Comparator.reverseOrder())
                .limit(topCount)
                .mapToDouble(Double::doubleValue)
                .sum();
                
        return new GlobalEconomyData(w, b, c, t10);
    }
}