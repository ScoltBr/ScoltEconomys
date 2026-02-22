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
}