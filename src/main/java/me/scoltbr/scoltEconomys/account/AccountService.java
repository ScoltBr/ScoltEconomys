// src/main/java/me/scoltbr/scoltEconomys/account/AccountService.java
package me.scoltbr.scoltEconomys.account;

import me.scoltbr.scoltEconomys.audit.TransactionAuditService;
import me.scoltbr.scoltEconomys.audit.TransactionType;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import me.scoltbr.scoltEconomys.tax.TaxManager;
import me.scoltbr.scoltEconomys.tax.TaxResult;
import me.scoltbr.scoltEconomys.tax.TaxType;
import me.scoltbr.scoltEconomys.util.LockOrder;
import me.scoltbr.scoltEconomys.util.Preconditions;
import me.scoltbr.scoltEconomys.util.StripedLocks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AccountService {

    private final Plugin plugin;
    private final AsyncExecutor async;
    private final AccountCache cache;
    private final AccountRepository repo;

    private final TaxManager taxManager;
    private final TreasuryService treasury;
    private final TransactionAuditService audit;

    private final StripedLocks<UUID> accountLocks;
    private final java.util.concurrent.ConcurrentHashMap<UUID, java.util.List<Consumer<PlayerAccount>>> pendingLoads;

    public AccountService(Plugin plugin,
                          AsyncExecutor async,
                          AccountCache cache,
                          AccountRepository repo,
                          TaxManager taxManager,
                          TreasuryService treasury,
                          TransactionAuditService audit) {
        this.plugin = plugin;
        this.async = async;
        this.cache = cache;
        this.repo = repo;
        this.taxManager = taxManager;
        this.treasury = treasury;
        this.audit = audit;

        this.accountLocks = new StripedLocks<>(256);
        this.pendingLoads = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public Optional<PlayerAccount> getCached(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Carrega (se necessário) e entrega na main thread via callback.
     * DB sempre no async.
     */
    public void getOrLoad(UUID uuid, Consumer<PlayerAccount> callback) {
        Optional<PlayerAccount> cached = cache.get(uuid);
        if (cached.isPresent()) {
            callback.accept(cached.get());
            return;
        }

        pendingLoads.compute(uuid, (key, list) -> {
            Optional<PlayerAccount> doubleCheck = cache.get(uuid);
            if (doubleCheck.isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(doubleCheck.get()));
                return list;
            }

            if (list != null) {
                list.add(callback);
                return list;
            }

            java.util.List<Consumer<PlayerAccount>> newList = new java.util.ArrayList<>();
            newList.add(callback);

            async.runAsync(() -> {
                PlayerAccount loaded = repo.load(uuid)
                        .orElseGet(() -> new PlayerAccount(
                                uuid,
                                plugin.getConfig().getDouble("defaults.wallet", 0.0),
                                plugin.getConfig().getDouble("defaults.bank", 0.0),
                                Instant.now()
                        ));

                cache.put(loaded);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    java.util.List<Consumer<PlayerAccount>> callbacks = pendingLoads.remove(uuid);
                    if (callbacks != null) {
                        for (Consumer<PlayerAccount> cb : callbacks) {
                            cb.accept(loaded);
                        }
                    }
                });
            });

            return newList;
        });
    }

    // ----------------------------
    // Wallet (mutations)
    // ----------------------------

    public void depositWallet(UUID uuid, double amount) {
        Preconditions.positive(amount, "amount");
        withLock(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);
            acc.addWallet(amount);
            cache.markDirty(uuid);
        });
    }

    public boolean withdrawWallet(UUID uuid, double amount) {
        Preconditions.positive(amount, "amount");
        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);
            if (acc.wallet() < amount) return false;
            acc.addWallet(-amount);
            cache.markDirty(uuid);
            return true;
        });
    }

    public void setWallet(UUID uuid, double value) {
        Preconditions.notNegative(value, "value");
        withLock(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);
            acc.setWallet(value);
            cache.markDirty(uuid);
        });
    }

    // ----------------------------
    // Transfer (wallet -> wallet)
    // ----------------------------

    public TransferResult transferWallet(UUID from, UUID to, double grossAmount) {
        Preconditions.positive(grossAmount, "grossAmount");
        if (from.equals(to)) return TransferResult.fail("same-account");

        return withLocks(from, to, () -> {
            PlayerAccount fromAcc = requireCached(from);
            PlayerAccount toAcc = requireCached(to);

            TaxResult tax = taxManager.apply(TaxType.TRANSFER, grossAmount);

            if (fromAcc.wallet() < grossAmount) {
                return TransferResult.fail("insufficient-funds");
            }

            fromAcc.addWallet(-grossAmount);
            cache.markDirty(from);

            toAcc.addWallet(tax.netAmount());
            cache.markDirty(to);

            treasury.collect(tax.feeAmount());

            audit.record(
                    TransactionType.PAY,
                    from,
                    to,
                    grossAmount,
                    tax.netAmount(),
                    tax.feeAmount(),
                    "command",
                    "money pay"
            );

            return TransferResult.ok(tax.netAmount(), tax.feeAmount());
        });
    }

    public record TransferResult(boolean success, double net, double fee, String reason) {
        public static TransferResult ok(double net, double fee) {
            return new TransferResult(true, net, fee, null);
        }

        public static TransferResult fail(String reason) {
            return new TransferResult(false, 0.0, 0.0, reason);
        }
    }

    // ----------------------------
    // Bank (wallet <-> bank)
    // ----------------------------

    public MoveResult depositToBank(UUID uuid, double amount) {
        Preconditions.positive(amount, "amount");

        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);

            if (acc.wallet() < amount) return MoveResult.fail("insufficient-wallet");

            double maxBank = plugin.getConfig().getDouble("bank.max-balance", Double.MAX_VALUE);
            if (acc.bank() + amount > maxBank) return MoveResult.fail("bank-limit");

            acc.addWallet(-amount);
            acc.addBank(amount);
            cache.markDirty(uuid);

            audit.record(
                    TransactionType.BANK_DEPOSIT,
                    uuid,
                    null,
                    amount,
                    amount,
                    0.0,
                    "command",
                    "money deposit"
            );

            return MoveResult.ok(amount, 0.0);
        });
    }

    public MoveResult withdrawFromBank(UUID uuid, double grossAmount) {
        Preconditions.positive(grossAmount, "grossAmount");

        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);

            if (acc.bank() < grossAmount) return MoveResult.fail("insufficient-bank");

            TaxResult tax = taxManager.apply(TaxType.WITHDRAW, grossAmount);

            acc.addBank(-grossAmount);
            acc.addWallet(tax.netAmount());
            cache.markDirty(uuid);

            treasury.collect(tax.feeAmount());

            audit.record(
                    TransactionType.BANK_WITHDRAW,
                    null,
                    uuid,
                    grossAmount,
                    tax.netAmount(),
                    tax.feeAmount(),
                    "command",
                    "money withdraw"
            );

            return MoveResult.ok(tax.netAmount(), tax.feeAmount());
        });
    }

    public record MoveResult(boolean success, double net, double fee, String reason) {
        public static MoveResult ok(double net, double fee) { return new MoveResult(true, net, fee, null); }
        public static MoveResult fail(String reason) { return new MoveResult(false, 0.0, 0.0, reason); }
    }

    // ----------------------------
    // Lock helpers
    // ----------------------------

    private void withLock(UUID uuid, Runnable task) {
        ReentrantLock lock = accountLocks.lockFor(uuid);
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }

    private <R> R withLockResult(UUID uuid, Supplier<R> task) {
        ReentrantLock lock = accountLocks.lockFor(uuid);
        lock.lock();
        try {
            return task.get();
        } finally {
            lock.unlock();
        }
    }

    private <R> R withLocks(UUID a, UUID b, Supplier<R> task) {
        UUID first = LockOrder.first(a, b);
        UUID second = LockOrder.second(a, b);

        ReentrantLock l1 = accountLocks.lockFor(first);
        ReentrantLock l2 = accountLocks.lockFor(second);

        l1.lock();
        try {
            l2.lock();
            try {
                return task.get();
            } finally {
                l2.unlock();
            }
        } finally {
            l1.unlock();
        }
    }

    // ----------------------------
    // Internals
    // ----------------------------

    private PlayerAccount requireCached(UUID uuid) {
        return cache.get(uuid).orElseThrow(() ->
                new IllegalStateException("Account not cached for uuid=" + uuid)
        );
    }

    public double applyBankInterest(UUID uuid, double rate, double capPerInterval) {
        if (rate <= 0) return 0.0;
        if (capPerInterval <= 0) return 0.0;

        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);

            double bank = acc.bank();
            if (bank <= 0) return 0.0;

            double interest = bank * rate;
            if (interest > capPerInterval) interest = capPerInterval;

            double maxBank = plugin.getConfig().getDouble("bank.max-balance", Double.MAX_VALUE);
            double room = maxBank - bank;
            if (room <= 0) return 0.0;

            if (interest > room) interest = room;
            if (interest <= 0) return 0.0;

            acc.addBank(interest);
            cache.markDirty(uuid);

            audit.record(
                    TransactionType.BANK_INTEREST,
                    null,
                    uuid,
                    interest,
                    interest,
                    0.0,
                    "scheduler",
                    "bank interest"
            );

            return interest;
        });
    }

    public java.util.OptionalDouble peekWallet(UUID uuid) {
        return cache.get(uuid).map(a -> java.util.OptionalDouble.of(a.wallet()))
                .orElse(java.util.OptionalDouble.empty());
    }

    // ----------------------------
    // Vault Sync API (Offline players)
    // ----------------------------

    public double getWalletSync(UUID uuid) {
        Optional<PlayerAccount> cached = cache.get(uuid);
        if (cached.isPresent()) {
            return cached.get().wallet();
        }
        return repo.getWalletBalanceSync(uuid).orElse(0.0);
    }

    public boolean addWalletSync(UUID uuid, double amount) {
        // Amount could be negative for withdraw
        Optional<PlayerAccount> cached = cache.get(uuid);
        if (cached.isPresent()) {
            return withLockResult(uuid, () -> {
                PlayerAccount acc = requireCached(uuid);
                if (acc.wallet() + amount < 0) return false;
                acc.addWallet(amount);
                cache.markDirty(uuid);
                return true;
            });
        }
        
        // Not in cache, update DB directly
        return repo.addWalletBalanceSync(uuid, amount);
    }

    // opcional: se você usa em algum lugar
    public UUID uuidOf(Player player) {
        return player.getUniqueId();
    }
}