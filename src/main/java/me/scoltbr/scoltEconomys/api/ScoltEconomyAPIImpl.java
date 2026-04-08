package me.scoltbr.scoltEconomys.api;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.account.TreasuryService;
import me.scoltbr.scoltEconomys.event.EventManager;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import me.scoltbr.scoltEconomys.stock.StockMarketService;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Consumer;

/**
 * Implementação da {@link ScoltEconomyAPI} que delega para os serviços internos do plugin.
 *
 * Esta classe é interna — os outros plugins devem depender apenas de {@link ScoltEconomyAPI}.
 */
public final class ScoltEconomyAPIImpl implements ScoltEconomyAPI {

    private static final String API_VERSION = "1.0";

    private final AccountService accountService;
    private final TreasuryService treasuryService;
    private final EventManager eventManager;
    @SuppressWarnings("unused")
    private final AsyncExecutor async;        // reservado para futuros métodos async internos
    @SuppressWarnings("unused")
    private final Plugin plugin;              // reservado para agendamento na main thread
    private final StockMarketService stockMarketService; // nullable

    public ScoltEconomyAPIImpl(Plugin plugin,
                                AsyncExecutor async,
                                AccountService accountService,
                                TreasuryService treasuryService,
                                EventManager eventManager,
                                StockMarketService stockMarketService) {
        this.plugin = plugin;
        this.async = async;
        this.accountService = accountService;
        this.treasuryService = treasuryService;
        this.eventManager = eventManager;
        this.stockMarketService = stockMarketService;
    }

    // -------------------------------------------------------
    // Versão
    // -------------------------------------------------------

    @Override
    public String apiVersion() {
        return API_VERSION;
    }

    // -------------------------------------------------------
    // Carteira (síncrono)
    // -------------------------------------------------------

    @Override
    public double getWallet(UUID uuid) {
        return accountService.getCached(uuid)
                .map(a -> a.wallet())
                .orElse(0.0);
    }

    @Override
    public double getBank(UUID uuid) {
        return accountService.getCached(uuid)
                .map(a -> a.bank())
                .orElse(0.0);
    }

    @Override
    public void depositWallet(UUID uuid, double amount) {
        if (amount <= 0) return;
        accountService.depositWallet(uuid, amount);
    }

    @Override
    public boolean withdrawWallet(UUID uuid, double amount) {
        if (amount <= 0) return false;
        return accountService.withdrawWallet(uuid, amount);
    }

    @Override
    public void setWallet(UUID uuid, double value) {
        if (value < 0) return;
        accountService.setWallet(uuid, value);
    }

    // -------------------------------------------------------
    // Carteira (assíncrono)
    // -------------------------------------------------------

    @Override
    public void getWalletAsync(UUID uuid, Consumer<Double> callback) {
        // Tenta cache primeiro; se não estiver, carrega do DB
        Optional<me.scoltbr.scoltEconomys.account.PlayerAccount> cached = accountService.getCached(uuid);
        if (cached.isPresent()) {
            callback.accept(cached.get().wallet());
            return;
        }
        accountService.getOrLoad(uuid, acc -> callback.accept(acc.wallet()));
    }

    // -------------------------------------------------------
    // Transferência
    // -------------------------------------------------------

    @Override
    public void transfer(UUID from, UUID to, double amount, Consumer<TransferResult> callback) {
        // loadamos ambos os jogadores e executamos na main thread (AccountService não é async)
        accountService.getOrLoad(from, fromAcc ->
            accountService.getOrLoad(to, toAcc -> {
                var internal = accountService.transferWallet(from, to, amount);
                callback.accept(internal.success()
                        ? TransferResult.ok(internal.net(), internal.fee())
                        : TransferResult.fail(internal.reason()));
            })
        );
    }

    // -------------------------------------------------------
    // Banco
    // -------------------------------------------------------

    @Override
    public void depositToBank(UUID uuid, double amount, Consumer<BankResult> callback) {
        accountService.getOrLoad(uuid, acc -> {
            var result = accountService.depositToBank(uuid, amount);
            callback.accept(result.success()
                    ? BankResult.ok(result.net(), result.fee())
                    : BankResult.fail(result.reason()));
        });
    }

    @Override
    public void withdrawFromBank(UUID uuid, double amount, Consumer<BankResult> callback) {
        accountService.getOrLoad(uuid, acc -> {
            var result = accountService.withdrawFromBank(uuid, amount);
            callback.accept(result.success()
                    ? BankResult.ok(result.net(), result.fee())
                    : BankResult.fail(result.reason()));
        });
    }

    // -------------------------------------------------------
    // Tesouro
    // -------------------------------------------------------

    @Override
    public double getTreasuryBalance() {
        return treasuryService.balance();
    }

    // -------------------------------------------------------
    // Evento Econômico
    // -------------------------------------------------------

    @Override
    public Optional<EcoEventInfo> getActiveEvent() {
        return eventManager.getActiveEvent().map(e -> new EcoEventInfo(
                e.id(),
                e.displayName(),
                e.interestMultiplier(),
                e.taxMultiplier(),
                eventManager.getRemainingSeconds()
        ));
    }

    // -------------------------------------------------------
    // Mercado de Ações
    // -------------------------------------------------------

    @Override
    public boolean isStockMarketEnabled() {
        return stockMarketService != null;
    }

    @Override
    public double getStockPrice(String stockId) {
        if (stockMarketService == null) return 0.0;
        return stockMarketService.currentPrice(stockId);
    }

    @Override
    public long getStockAvailableShares(String stockId) {
        if (stockMarketService == null) return 0L;
        return stockMarketService.availableShares(stockId);
    }

    @Override
    public void getStockPortfolio(UUID uuid, Consumer<Map<String, StockHoldingInfo>> callback) {
        if (stockMarketService == null) {
            callback.accept(Map.of());
            return;
        }
        stockMarketService.getPortfolioAsync(uuid, holdings -> {
            Map<String, StockHoldingInfo> result = new LinkedHashMap<>();
            holdings.forEach((id, h) -> {
                double cur = stockMarketService.currentPrice(id);
                result.put(id, new StockHoldingInfo(id, h.quantity(), h.avgPrice(), cur));
            });
            callback.accept(result);
        });
    }
}
