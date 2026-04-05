package me.scoltbr.scoltEconomys.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class AccountCache {

    private final Cache<UUID, PlayerAccount> accounts = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(2_000)
            .build();
            
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

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
}