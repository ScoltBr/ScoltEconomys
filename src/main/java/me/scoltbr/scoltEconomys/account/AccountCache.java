package me.scoltbr.scoltEconomys.account;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AccountCache {

    private final Map<UUID, PlayerAccount> accounts = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    public Optional<PlayerAccount> get(UUID uuid) {
        return Optional.ofNullable(accounts.get(uuid));
    }

    public void put(PlayerAccount account) {
        accounts.put(account.uuid(), account);
    }

    public void remove(UUID uuid) {
        accounts.remove(uuid);
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
            PlayerAccount acc = accounts.get(uuid);
            if (acc != null) batch.add(acc);
        }
        return batch;
    }

    public Collection<PlayerAccount> allAccounts() {
        return accounts.values();
    }

    public Collection<PlayerAccount> allAccountsSnapshot() {
        return List.copyOf(accounts.values());
    }

    public java.util.Set<java.util.UUID> cachedUuids() {
        return java.util.Set.copyOf(accounts.keySet());
    }

    public void requeueDirty(UUID uuid) {
        dirty.add(uuid);
    }
}