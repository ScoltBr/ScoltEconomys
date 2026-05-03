package me.scoltbr.scoltEconomys.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AccountCache {

    // Adicionado callback para salvar conta suja caso ela seja expulsa (evicted) do cache por inatividade
    private java.util.function.Consumer<PlayerAccount> evictionHandler = null;

    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    private final Cache<UUID, PlayerAccount> accounts = Caffeine.newBuilder()
            // Trocado de expireAfterWrite para expireAfterAccess para não expirar contas de jogadores ativos
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(2_000)
            .removalListener((UUID key, PlayerAccount value, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                if (cause.wasEvicted() && value != null && dirty.contains(key)) {
                    if (evictionHandler != null) {
                        evictionHandler.accept(value);
                    }
                    dirty.remove(key);
                }
            })
            .build();

    public Optional<PlayerAccount> get(UUID uuid) {
        return Optional.ofNullable(accounts.getIfPresent(uuid));
    }

    public void put(PlayerAccount account) {
        accounts.put(account.uuid(), account);
    }

    public void remove(UUID uuid) {
        accounts.invalidate(uuid);
        dirty.remove(uuid);
    }

    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    public List<PlayerAccount> pollDirtyBatch(int max) {
        if (max <= 0) return List.of();

        List<PlayerAccount> batch = new ArrayList<>(Math.min(max, dirty.size()));
        Iterator<UUID> it = dirty.iterator();

        while (it.hasNext() && batch.size() < max) {
            UUID uuid = it.next();
            it.remove();
            PlayerAccount acc = accounts.getIfPresent(uuid);
            if (acc != null) batch.add(acc);
        }
        return batch;
    }

    public Collection<PlayerAccount> allAccounts() {
        return accounts.asMap().values();
    }

    public Collection<PlayerAccount> allAccountsSnapshot() {
        return List.copyOf(accounts.asMap().values());
    }

    public java.util.Set<java.util.UUID> cachedUuids() {
        return java.util.Set.copyOf(accounts.asMap().keySet());
    }

    public void requeueDirty(UUID uuid) {
        dirty.add(uuid);
    }

    public void setEvictionHandler(java.util.function.Consumer<PlayerAccount> evictionHandler) {
        this.evictionHandler = evictionHandler;
    }
}