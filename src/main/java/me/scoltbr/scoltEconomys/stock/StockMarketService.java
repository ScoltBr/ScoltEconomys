package me.scoltbr.scoltEconomys.stock;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.account.TreasuryService;
import me.scoltbr.scoltEconomys.event.EventManager;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import me.scoltbr.scoltEconomys.api.event.StockPriceUpdateEvent;
import me.scoltbr.scoltEconomys.api.event.StockTransactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Serviço central do Mercado de Ações Virtual.
 *
 * Responsabilidades:
 * - Carregar definições de empresas do config.yml
 * - Manter preços correntes em cache (ConcurrentHashMap)
 * - Executar o tick de oscilação de preços (drift + pressão + eventBoost)
 * - Processar compras e vendas de forma thread-safe
 */
public final class StockMarketService {

    private final Plugin plugin;
    private final AsyncExecutor async;
    private final StockRepository repo;
    private final AccountService accountService;
    private final TreasuryService treasury;
    private final EventManager eventManager;

    /** Definições carregadas do config.yml */
    private final Map<String, Stock> stocks = new LinkedHashMap<>();

    /** Preços correntes em memória — atualizados pelo ticker */
    private final ConcurrentHashMap<String, Double> currentPrices = new ConcurrentHashMap<>();

    /**
     * Total de ações em posse de jogadores por empresa — para verificar disponibilidade.
     * Carregado do DB no startup e mantido sincronizado a cada compra/venda.
     */
    private final ConcurrentHashMap<String, AtomicLong> heldSharesCache = new ConcurrentHashMap<>();

    /** Contadores de pressão de mercado — resetados a cada tick */
    private final ConcurrentHashMap<String, AtomicLong> buyPressure  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> sellPressure = new ConcurrentHashMap<>();

    /** Lock por empresa para garantir atomicidade de supply-check + atualização de holding */
    private final ConcurrentHashMap<String, ReentrantLock> stockLocks = new ConcurrentHashMap<>();

    // Parâmetros de oscilação lidos do config
    private double pressureFactor;
    private double maxChangePct;
    private double minPrice;
    private int historyKeep;

    public StockMarketService(Plugin plugin,
                               AsyncExecutor async,
                               StockRepository repo,
                               AccountService accountService,
                               TreasuryService treasury,
                               EventManager eventManager) {
        this.plugin = plugin;
        this.async = async;
        this.repo = repo;
        this.accountService = accountService;
        this.treasury = treasury;
        this.eventManager = eventManager;
        loadConfig();
    }

    // -------------------------------------------------------
    // Inicialização
    // -------------------------------------------------------

    private void loadConfig() {
        ConfigurationSection sm = plugin.getConfig().getConfigurationSection("stock-market");
        if (sm == null) return;

        pressureFactor = sm.getDouble("pressure-factor", 0.10);
        maxChangePct   = sm.getDouble("max-price-change-pct", 0.15);
        minPrice       = sm.getDouble("min-price", 0.01);
        historyKeep    = sm.getInt("history-keep", 48);
        double globalFee = sm.getDouble("brokerage-fee", 0.01);

        ConfigurationSection companies = sm.getConfigurationSection("companies");
        if (companies == null) return;

        for (String id : companies.getKeys(false)) {
            ConfigurationSection c = companies.getConfigurationSection(id);
            if (c == null) continue;

            Stock s = new Stock(
                    id,
                    c.getString("name", id),
                    c.getString("sector", "default"),
                    c.getDouble("initial-price", 100.0),
                    c.getDouble("volatility", 0.05),
                    c.getLong("total-shares", 100_000L),
                    c.getDouble("brokerage-fee", globalFee)
            );
            stocks.put(id, s);
            currentPrices.put(id, s.initialPrice());
            stockLocks.put(id, new ReentrantLock());
            buyPressure.put(id, new AtomicLong(0));
            sellPressure.put(id, new AtomicLong(0));
        }
    }

    /**
     * Deve ser chamado após carregar o banco.
     * Carrega o último preço salvo e o total de ações em posse para cada empresa.
     */
    public void loadInitialState() {
        for (Stock s : stocks.values()) {
            // Último preço persistido
            List<StockPrice> history = repo.getHistory(s.id(), 1);
            if (!history.isEmpty()) {
                currentPrices.put(s.id(), history.get(0).price());
            }
            // Total held (oferta consumida)
            long held = repo.sumHeldShares(s.id());
            heldSharesCache.put(s.id(), new AtomicLong(held));
        }
    }

    // -------------------------------------------------------
    // Consultas de mercado (chamadas na main thread — dados em memória)
    // -------------------------------------------------------

    public Map<String, Stock> getStocks() {
        return Collections.unmodifiableMap(stocks);
    }

    public Optional<Stock> getStock(String id) {
        return Optional.ofNullable(stocks.get(id));
    }

    public double currentPrice(String stockId) {
        return currentPrices.getOrDefault(stockId, 0.0);
    }

    public long availableShares(String stockId) {
        Stock s = stocks.get(stockId);
        if (s == null) return 0;
        AtomicLong held = heldSharesCache.get(stockId);
        return Math.max(0, s.totalShares() - (held == null ? 0 : held.get()));
    }

    // -------------------------------------------------------
    // Compra (async)
    // -------------------------------------------------------

    public void buyAsync(UUID uuid, String stockId, long qty, Consumer<BuyResult> callback) {
        async.runAsync(() -> {
            BuyResult result = buySync(uuid, stockId, qty);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    private BuyResult buySync(UUID uuid, String stockId, long qty) {
        if (qty <= 0) return BuyResult.fail("invalid-quantity");

        Stock stock = stocks.get(stockId);
        if (stock == null) return BuyResult.fail("unknown-stock");

        ReentrantLock lock = stockLocks.get(stockId);
        lock.lock();
        try {
            long available = availableShares(stockId);
            if (available < qty) return BuyResult.fail("insufficient-supply");

            double price = currentPrices.getOrDefault(stockId, stock.initialPrice());
            double cost  = price * qty;
            double fee   = cost * stock.brokerageFee();
            double total = cost + fee;

            // Debita da carteira (AccountService usa StripedLocks por UUID)
            boolean ok = accountService.withdrawWallet(uuid, total);
            if (!ok) return BuyResult.fail("insufficient-funds");

            // Taxa para o tesouro
            if (fee > 0) treasury.collect(fee);

            // Atualiza cache de ações em posse
            heldSharesCache.computeIfAbsent(stockId, k -> new AtomicLong(0)).addAndGet(qty);

            // Pressão de mercado
            buyPressure.get(stockId).addAndGet(qty);

            // Persiste holding e transação no DB (ainda estamos em thread async)
            StockHolding current = repo.getHolding(uuid, stockId)
                    .orElse(new StockHolding(uuid, stockId, 0, price));
            repo.upsertHolding(current.add(qty, price));
            repo.recordTransaction(uuid, stockId, "BUY", qty, price, total);

            // Fire event (sync)
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(new StockTransactionEvent(
                        uuid, stockId, StockTransactionEvent.TransactionType.BUY, qty, price, total, fee
                ));
            });

            return BuyResult.ok(qty, total, fee, price);
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------
    // Venda (async)
    // -------------------------------------------------------

    public void sellAsync(UUID uuid, String stockId, long qty, Consumer<SellResult> callback) {
        async.runAsync(() -> {
            SellResult result = sellSync(uuid, stockId, qty);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    private SellResult sellSync(UUID uuid, String stockId, long qty) {
        if (qty <= 0) return SellResult.fail("invalid-quantity");

        Stock stock = stocks.get(stockId);
        if (stock == null) return SellResult.fail("unknown-stock");

        ReentrantLock lock = stockLocks.get(stockId);
        lock.lock();
        try {
            Optional<StockHolding> holdingOpt = repo.getHolding(uuid, stockId);
            if (holdingOpt.isEmpty() || holdingOpt.get().quantity() < qty) {
                return SellResult.fail("insufficient-holding");
            }

            StockHolding holding = holdingOpt.get();
            double price    = currentPrices.getOrDefault(stockId, stock.initialPrice());
            double proceeds = price * qty;
            double fee      = proceeds * stock.brokerageFee();
            double net      = proceeds - fee;
            double profit   = (price - holding.avgPrice()) * qty - fee;

            // Taxa para o tesouro
            if (fee > 0) treasury.collect(fee);

            // Credita na carteira
            accountService.depositWallet(uuid, net);

            // Atualiza cache
            heldSharesCache.computeIfAbsent(stockId, k -> new AtomicLong(0)).addAndGet(-qty);

            // Pressão de mercado
            sellPressure.get(stockId).addAndGet(qty);

            // Persiste
            StockHolding updated = holding.remove(qty);
            if (updated.quantity() <= 0) {
                repo.deleteHolding(uuid, stockId);
            } else {
                repo.upsertHolding(updated);
            }
            repo.recordTransaction(uuid, stockId, "SELL", qty, price, net);

            // Fire event (sync)
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(new StockTransactionEvent(
                        uuid, stockId, StockTransactionEvent.TransactionType.SELL, qty, price, net, fee
                ));
            });

            return SellResult.ok(qty, net, fee, profit, price);
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------
    // Consultas assíncronas para GUIs
    // -------------------------------------------------------

    public void getPortfolioAsync(UUID uuid, Consumer<Map<String, StockHolding>> callback) {
        async.runAsync(() -> {
            var holdings = repo.getAllHoldings(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(holdings));
        });
    }

    public void getTopHoldersAsync(String stockId, int limit, Consumer<List<StockHolding>> callback) {
        async.runAsync(() -> {
            var top = repo.getTopHolders(stockId, limit);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(top));
        });
    }

    public void getPriceHistoryAsync(String stockId, int limit, Consumer<List<StockPrice>> callback) {
        async.runAsync(() -> {
            var history = repo.getHistory(stockId, limit);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(history));
        });
    }

    // -------------------------------------------------------
    // Ticker de oscilação (chamado pelo scheduler async)
    // -------------------------------------------------------

    public void tick() {
        Random rand = new Random();

        for (Stock stock : stocks.values()) {
            double cur = currentPrices.getOrDefault(stock.id(), stock.initialPrice());

            // 1. Drift aleatório (distribuição normal, desvio = volatilidade da empresa)
            double drift    = rand.nextGaussian() * stock.volatility();

            // 2. Pressão de mercado: (compras - vendas) / total transações
            long buys   = buyPressure.get(stock.id()).getAndSet(0);
            long sells  = sellPressure.get(stock.id()).getAndSet(0);
            long total  = buys + sells;
            double pressure = total > 0 ? ((double)(buys - sells) / total) * pressureFactor : 0.0;

            // 3. Boost do evento econômico ativo para o setor desta empresa
            double sectorBoost = eventManager.getSectorBoost(stock.sector());

            // 4. Calcula novo preço e aplica limites
            double changePct = drift + pressure + sectorBoost;
            changePct = Math.max(-maxChangePct, Math.min(maxChangePct, changePct));
            double newPrice = Math.max(minPrice, cur * (1.0 + changePct));

            currentPrices.put(stock.id(), newPrice);
            repo.savePrice(new StockPrice(stock.id(), newPrice, System.currentTimeMillis()));

            // Batched sync events
            final double finalOld = cur;
            final double finalNew = newPrice;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(new StockPriceUpdateEvent(stock.id(), finalOld, finalNew));
            });
        }

        // Limpeza de histórico antigo (assíncrona, baixa prioridade)
        cleanOldHistory();
    }

    private void cleanOldHistory() {
        for (Stock stock : stocks.values()) {
            repo.purgeOldPrices(stock.id(), historyKeep);
        }
    }
}

