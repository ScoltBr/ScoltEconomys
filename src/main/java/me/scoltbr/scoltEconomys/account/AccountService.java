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
import me.scoltbr.scoltEconomys.api.event.AccountBalanceChangeEvent;
import me.scoltbr.scoltEconomys.api.event.AccountBalanceChangeEvent.BalanceType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
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
                                java.math.BigDecimal.valueOf(plugin.getConfig().getDouble("defaults.wallet", 0.0)),
                                java.math.BigDecimal.valueOf(plugin.getConfig().getDouble("defaults.bank", 0.0)),
                                Instant.now()));

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

    /**
     * Deposita uma quantia na carteira (wallet) de um jogador.
     * <p>
     * Esta operação é síncrona e exige que o jogador esteja em cache.
     * </p>
     *
     * @param uuid   UUID do jogador.
     * @param amount Quantia positiva a depositar.
     */
    public void depositWallet(UUID uuid, java.math.BigDecimal amount) {
        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive");
        withLock(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);
            java.math.BigDecimal old = acc.wallet();
            AccountBalanceChangeEvent event = fireBalanceChange(uuid, old, old.add(amount), BalanceType.WALLET);
            if (!event.isCancelled()) {
                acc.setWallet(event.getNewBalance());
                cache.markDirty(uuid);
            }
        });
    }

    /**
     * Retira uma quantia da carteira (wallet) do jogador.
     *
     * @param uuid   UUID do jogador.
     * @param amount Quantia positiva a retirar.
     * @return true se a retirada foi concluída, false se saldo insuficiente ou
     *         cancelada.
     */
    public boolean withdrawWallet(UUID uuid, java.math.BigDecimal amount) {
        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive");
        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);
            if (acc.wallet().compareTo(amount) < 0)
                return false;
            java.math.BigDecimal old = acc.wallet();
            AccountBalanceChangeEvent event = fireBalanceChange(uuid, old, old.subtract(amount), BalanceType.WALLET);
            if (!event.isCancelled()) {
                acc.setWallet(event.getNewBalance());
                cache.markDirty(uuid);
                return true;
            }
            return false;
        });
    }

    /**
     * Define o saldo da carteira do jogador para um valor absoluto.
     *
     * @param uuid  UUID do jogador.
     * @param value Novo saldo.
     */
    public void setWallet(UUID uuid, java.math.BigDecimal value) {
        if (value.compareTo(java.math.BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("value must not be negative");
        withLock(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);
            java.math.BigDecimal old = acc.wallet();
            AccountBalanceChangeEvent event = fireBalanceChange(uuid, old, value, BalanceType.WALLET);
            if (!event.isCancelled()) {
                acc.setWallet(event.getNewBalance());
                cache.markDirty(uuid);
            }
        });
    }

    /**
     * Transfere valores entre dois jogadores, aplicando impostos e disparando
     * eventos.
     *
     * @param from        UUID do remetente.
     * @param to          UUID do destinatário.
     * @param grossAmount Valor bruto enviado pelo remetente.
     * @return Resultado detalhado da transferência.
     */
    public TransferResult transferWallet(UUID from, UUID to, java.math.BigDecimal grossAmount) {
        if (grossAmount.compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("grossAmount must be positive");
        if (from.equals(to))
            return TransferResult.fail("same-account");

        return withLocks(from, to, () -> {
            PlayerAccount fromAcc = requireCached(from);
            PlayerAccount toAcc = requireCached(to);

            TaxResult tax = taxManager.apply(TaxType.TRANSFER, grossAmount);

            if (fromAcc.wallet().compareTo(grossAmount) < 0) {
                return TransferResult.fail("insufficient-funds");
            }

            // Fira evento for 'from'
            AccountBalanceChangeEvent eFrom = fireBalanceChange(from, fromAcc.wallet(),
                    fromAcc.wallet().subtract(grossAmount), BalanceType.WALLET);
            if (eFrom.isCancelled()) {
                return TransferResult.fail("cancelled-by-api");
            }
            // Fira evento for 'to'
            AccountBalanceChangeEvent eTo = fireBalanceChange(to, toAcc.wallet(), toAcc.wallet().add(tax.netAmount()),
                    BalanceType.WALLET);
            if (eTo.isCancelled()) {
                return TransferResult.fail("cancelled-by-api");
            }

            fromAcc.setWallet(eFrom.getNewBalance());
            cache.markDirty(from);

            toAcc.setWallet(eTo.getNewBalance());
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
                    "money pay");

            return TransferResult.ok(tax.netAmount(), tax.feeAmount());
        });
    }

    public record TransferResult(boolean success, java.math.BigDecimal net, java.math.BigDecimal fee, String reason) {
        public static TransferResult ok(java.math.BigDecimal net, java.math.BigDecimal fee) {
            return new TransferResult(true, net, fee, null);
        }

        public static TransferResult fail(String reason) {
            return new TransferResult(false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, reason);
        }
    }

    // ----------------------------
    // Bank (wallet <-> bank)
    // ----------------------------

    public MoveResult depositToBank(UUID uuid, java.math.BigDecimal amount) {
        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("amount must be positive");

        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);

            if (acc.wallet().compareTo(amount) < 0)
                return MoveResult.fail("insufficient-wallet");

            java.math.BigDecimal maxBank = java.math.BigDecimal
                    .valueOf(plugin.getConfig().getDouble("bank.max-balance", Double.MAX_VALUE));
            if (acc.bank().add(amount).compareTo(maxBank) > 0)
                return MoveResult.fail("bank-limit");

            // Evento Carteira (Saída)
            AccountBalanceChangeEvent eWallet = fireBalanceChange(uuid, acc.wallet(), acc.wallet().subtract(amount),
                    BalanceType.WALLET);
            if (eWallet.isCancelled())
                return MoveResult.fail("cancelled-by-api");

            // Evento Banco (Entrada)
            AccountBalanceChangeEvent eBank = fireBalanceChange(uuid, acc.bank(), acc.bank().add(amount),
                    BalanceType.BANK);
            if (eBank.isCancelled())
                return MoveResult.fail("cancelled-by-api");

            acc.setWallet(eWallet.getNewBalance());
            acc.setBank(eBank.getNewBalance());
            cache.markDirty(uuid);

            audit.record(
                    TransactionType.BANK_DEPOSIT,
                    uuid,
                    null,
                    amount,
                    amount,
                    java.math.BigDecimal.ZERO,
                    "command",
                    "money deposit");

            return MoveResult.ok(amount, java.math.BigDecimal.ZERO);
        });
    }

    public MoveResult withdrawFromBank(UUID uuid, java.math.BigDecimal grossAmount) {
        if (grossAmount.compareTo(java.math.BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("grossAmount must be positive");

        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);

            if (acc.bank().compareTo(grossAmount) < 0)
                return MoveResult.fail("insufficient-bank");

            TaxResult tax = taxManager.apply(TaxType.WITHDRAW, grossAmount);

            // Evento Banco (Saída)
            AccountBalanceChangeEvent eBank = fireBalanceChange(uuid, acc.bank(), acc.bank().subtract(grossAmount),
                    BalanceType.BANK);
            if (eBank.isCancelled())
                return MoveResult.fail("cancelled-by-api");

            // Evento Carteira (Entrada)
            AccountBalanceChangeEvent eWallet = fireBalanceChange(uuid, acc.wallet(), acc.wallet().add(tax.netAmount()),
                    BalanceType.WALLET);
            if (eWallet.isCancelled())
                return MoveResult.fail("cancelled-by-api");

            acc.setBank(eBank.getNewBalance());
            acc.setWallet(eWallet.getNewBalance());
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
                    "money withdraw");

            return MoveResult.ok(tax.netAmount(), tax.feeAmount());
        });
    }

    public record MoveResult(boolean success, java.math.BigDecimal net, java.math.BigDecimal fee, String reason) {
        public static MoveResult ok(java.math.BigDecimal net, java.math.BigDecimal fee) {
            return new MoveResult(true, net, fee, null);
        }

        public static MoveResult fail(String reason) {
            return new MoveResult(false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, reason);
        }
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

    private AccountBalanceChangeEvent fireBalanceChange(UUID uuid, java.math.BigDecimal oldBal,
            java.math.BigDecimal newBal, BalanceType type) {
        AccountBalanceChangeEvent event = new AccountBalanceChangeEvent(uuid, oldBal, newBal, type);
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
            return event;
        }

        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                Bukkit.getPluginManager().callEvent(event);
                return event;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to fire AccountBalanceChangeEvent synchronously from async thread for " + uuid, e);
            event.setCancelled(true);
            return event;
        }
    }

    private PlayerAccount requireCached(UUID uuid) {
        return cache.get(uuid).orElseThrow(() -> new IllegalStateException("Account not cached for uuid=" + uuid));
    }

    public java.math.BigDecimal applyBankInterest(UUID uuid, double rate, double capPerInterval) {
        if (rate <= 0)
            return java.math.BigDecimal.ZERO;
        if (capPerInterval <= 0)
            return java.math.BigDecimal.ZERO;

        return withLockResult(uuid, () -> {
            PlayerAccount acc = requireCached(uuid);

            java.math.BigDecimal bank = acc.bank();
            if (bank.compareTo(java.math.BigDecimal.ZERO) <= 0)
                return java.math.BigDecimal.ZERO;

            java.math.BigDecimal interest = bank.multiply(java.math.BigDecimal.valueOf(rate)).setScale(2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal cap = java.math.BigDecimal.valueOf(capPerInterval);
            if (interest.compareTo(cap) > 0)
                interest = cap;

            java.math.BigDecimal maxBank = java.math.BigDecimal
                    .valueOf(plugin.getConfig().getDouble("bank.max-balance", Double.MAX_VALUE));
            java.math.BigDecimal room = maxBank.subtract(bank);
            if (room.compareTo(java.math.BigDecimal.ZERO) <= 0)
                return java.math.BigDecimal.ZERO;

            if (interest.compareTo(room) > 0)
                interest = room;
            if (interest.compareTo(java.math.BigDecimal.ZERO) <= 0)
                return java.math.BigDecimal.ZERO;

            AccountBalanceChangeEvent event = fireBalanceChange(uuid, bank, bank.add(interest), BalanceType.BANK);
            if (event.isCancelled())
                return java.math.BigDecimal.ZERO;

            acc.setBank(event.getNewBalance());
            cache.markDirty(uuid);

            // Re-calculate interest effectively applied (in case a listener changed it)
            java.math.BigDecimal effectivelyAdded = event.getNewBalance().subtract(bank);

            audit.record(
                    TransactionType.BANK_INTEREST,
                    null,
                    uuid,
                    effectivelyAdded,
                    effectivelyAdded,
                    java.math.BigDecimal.ZERO,
                    "scheduler",
                    "bank interest");

            return effectivelyAdded;
        });
    }

    public Optional<java.math.BigDecimal> peekWallet(UUID uuid) {
        return cache.get(uuid).map(a -> a.wallet());
    }

    // ----------------------------
    // Vault Sync API (Offline players)
    // ----------------------------

    public java.math.BigDecimal getWalletSync(UUID uuid) {
        Optional<PlayerAccount> cached = cache.get(uuid);
        if (cached.isPresent()) {
            return cached.get().wallet();
        }
        return repo.getWalletBalanceSync(uuid).orElse(java.math.BigDecimal.ZERO);
    }

    public boolean addWalletSync(UUID uuid, java.math.BigDecimal amount) {
        // Amount could be negative for withdraw
        Optional<PlayerAccount> cached = cache.get(uuid);
        if (cached.isPresent()) {
            return withLockResult(uuid, () -> {
                PlayerAccount acc = requireCached(uuid);
                if (acc.wallet().add(amount).compareTo(java.math.BigDecimal.ZERO) < 0)
                    return false;
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