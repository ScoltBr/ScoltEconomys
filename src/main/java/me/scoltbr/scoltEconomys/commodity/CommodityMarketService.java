package me.scoltbr.scoltEconomys.commodity;

import me.scoltbr.scoltEconomys.account.AccountService;
import me.scoltbr.scoltEconomys.account.TreasuryService;
import me.scoltbr.scoltEconomys.event.EventManager;
import me.scoltbr.scoltEconomys.scheduler.AsyncExecutor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Serviço central do Mercado de Commodities.
 *
 * <p>Transforma recursos do Minecraft (carvão, ferro, diamante...) em ativos
 * negociáveis com preço dinâmico baseado na oferta real gerada pelos jogadores.</p>
 *
 * <h2>Dinâmica de preços</h2>
 * <ol>
 *   <li><b>Mean-reversion</b>: o preço gravita lentamente em direção ao preço inicial.</li>
 *   <li><b>Pressão de oferta</b>: quanto mais jogadores vendem, mais o preço cai.</li>
 *   <li><b>Volatilidade aleatória</b>: ruído gaussiano proporcional à volatilidade da commodity.</li>
 *   <li><b>Sector boost</b>: eventos econômicos ativos afetam setores específicos.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <ul>
 *   <li>{@link #sell} executa na <b>main thread</b> (acesso ao inventário).</li>
 *   <li>{@link #tick} executa em thread <b>async</b> (scheduler).</li>
 *   <li>Preços em {@code ConcurrentHashMap} são safe para leitura multi-thread.</li>
 * </ul>
 */
public final class CommodityMarketService {

    private final Plugin plugin;
    private final AsyncExecutor async;
    private final CommodityRepository repo;
    private final AccountService accountService;
    private final TreasuryService treasury;
    private final EventManager eventManager;

    /** Definições carregadas do config.yml */
    private final Map<String, Commodity> commodities = new LinkedHashMap<>();

    /** Preços correntes em memória, atualizados pelo ticker */
    private final ConcurrentHashMap<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();

    /**
     * Volume de venda acumulado desde o último tick (em unidades de item).
     * Resetado a cada tick após ser consumido no cálculo de pressão.
     */
    private final ConcurrentHashMap<String, AtomicLong> sellVolume = new ConcurrentHashMap<>();

    // Parâmetros globais lidos do config
    private double pressureFactor;
    private double meanReversionRate;
    private double minPriceRatio;
    private double maxPriceRatio;
    private int historyKeep;

    public CommodityMarketService(Plugin plugin,
                                   AsyncExecutor async,
                                   CommodityRepository repo,
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
        ConfigurationSection cm = plugin.getConfig().getConfigurationSection("commodity-market");
        if (cm == null) return;

        pressureFactor    = cm.getDouble("pressure-factor", 0.08);
        meanReversionRate = cm.getDouble("mean-reversion-rate", 0.03);
        minPriceRatio     = cm.getDouble("min-price-ratio", 0.20);
        maxPriceRatio     = cm.getDouble("max-price-ratio", 5.00);
        historyKeep       = cm.getInt("history-keep", 96);

        double globalFee = cm.getDouble("brokerage-fee", 0.02);

        ConfigurationSection comms = cm.getConfigurationSection("commodities");
        if (comms == null) return;

        for (String id : comms.getKeys(false)) {
            ConfigurationSection c = comms.getConfigurationSection(id);
            if (c == null) continue;

            String matName = c.getString("material", "BARRIER");
            Material material;
            try {
                material = Material.valueOf(matName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[CommodityMarket] Material inválido '" + matName + "' para commodity '" + id + "'. Pulando.");
                continue;
            }

            Commodity commodity = new Commodity(
                    id,
                    c.getString("name", id),
                    c.getString("sector", "default"),
                    material,
                    BigDecimal.valueOf(c.getDouble("initial-price", 10.0)),
                    c.getDouble("volatility", 0.05),
                    c.getInt("max-per-transaction", 1000),
                    BigDecimal.valueOf(c.getDouble("brokerage-fee", globalFee))
            );

            commodities.put(id, commodity);
            currentPrices.put(id, commodity.initialPrice());
            sellVolume.put(id, new AtomicLong(0));
        }

        plugin.getLogger().info("[CommodityMarket] " + commodities.size() + " commodities carregadas.");
    }

    /**
     * Carrega o último preço persistido do banco para cada commodity.
     * Deve ser chamado após as migrations e antes de iniciar o ticker.
     */
    public void loadInitialState() {
        for (Commodity c : commodities.values()) {
            List<CommodityPrice> history = repo.getHistory(c.id(), 1);
            if (!history.isEmpty()) {
                currentPrices.put(c.id(), history.get(history.size() - 1).price());
            }
        }
    }

    // -------------------------------------------------------
    // Consultas (main thread safe — leitura de ConcurrentHashMap)
    // -------------------------------------------------------

    public Map<String, Commodity> getCommodities() {
        return Collections.unmodifiableMap(commodities);
    }

    public Optional<Commodity> getCommodity(String id) {
        return Optional.ofNullable(commodities.get(id));
    }

    /** Retorna o preço corrente em memória. Seguro para chamar em qualquer thread. */
    public BigDecimal currentPrice(String commodityId) {
        return currentPrices.getOrDefault(commodityId, BigDecimal.ZERO);
    }

    /**
     * Retorna a commodity cujo {@link Commodity#material()} corresponde ao material dado,
     * ou {@link Optional#empty()} se nenhuma corresponder.
     */
    public Optional<Commodity> findByMaterial(Material material) {
        return commodities.values().stream()
                .filter(c -> c.material() == material)
                .findFirst();
    }

    // -------------------------------------------------------
    // Venda (MAIN THREAD — acessa inventário do jogador)
    // -------------------------------------------------------

    /**
     * Processa a venda de {@code requestedQty} unidades de {@code commodityId} pelo jogador.
     *
     * <p><b>Deve ser chamado na main thread</b>, pois acessa e modifica o inventário do jogador.</p>
     *
     * @param player       Jogador vendendo.
     * @param commodityId  ID da commodity (chave do config).
     * @param requestedQty Quantidade a vender; {@code -1} para vender tudo.
     * @return Resultado detalhado da operação.
     */
    public SellCommodityResult sell(Player player, String commodityId, int requestedQty) {
        Commodity commodity = commodities.get(commodityId);
        if (commodity == null) return SellCommodityResult.fail("unknown-commodity");

        // Conta quantos itens o jogador possui
        int available = countItems(player, commodity.material());
        if (available <= 0) return SellCommodityResult.fail("no-items");

        // Resolve quantidade: -1 = tudo, respeitando o limite por transação
        int qty = (requestedQty == -1)
                ? Math.min(available, commodity.maxPerTransaction())
                : requestedQty;

        if (qty <= 0) return SellCommodityResult.fail("invalid-quantity");
        if (qty > commodity.maxPerTransaction())
            return SellCommodityResult.fail("exceeds-limit-" + commodity.maxPerTransaction());
        if (qty > available) return SellCommodityResult.fail("insufficient-items");

        BigDecimal price = currentPrices.getOrDefault(commodityId, commodity.initialPrice());
        BigDecimal gross = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee   = gross.multiply(commodity.brokerageFee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal net   = gross.subtract(fee);

        // Credita na carteira — AccountService exige conta em cache (jogador online → sempre em cache)
        try {
            accountService.depositWallet(player.getUniqueId(), net);
        } catch (IllegalStateException e) {
            return SellCommodityResult.fail("account-not-cached");
        }

        // Remove os itens do inventário
        removeItems(player, commodity.material(), qty);

        // Taxa para o Tesouro
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            treasury.collect(fee);
        }

        // Registra pressão de venda para o próximo tick de preço
        sellVolume.computeIfAbsent(commodityId, k -> new AtomicLong(0)).addAndGet(qty);

        // Persiste transação de forma assíncrona (não bloqueia a main thread)
        final BigDecimal finalPrice = price;
        final BigDecimal finalGross = gross;
        async.runAsync(() -> {
            repo.recordTransaction(player.getUniqueId(), commodityId, "SELL", qty, finalPrice, finalGross);
        });

        return SellCommodityResult.ok(qty, net, fee, price);
    }

    // -------------------------------------------------------
    // Ticker de oscilação (ASYNC — chamado pelo scheduler)
    // -------------------------------------------------------

    /**
     * Recalcula os preços de todas as commodities com base em:
     * pressão de oferta acumulada, drift aleatório, mean-reversion e sector boost.
     */
    public void tick() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (Commodity commodity : commodities.values()) {
            BigDecimal current = currentPrices.getOrDefault(commodity.id(), commodity.initialPrice());
            BigDecimal initial = commodity.initialPrice();

            // 1. Pressão de oferta — quanto mais jogadores venderam, mais o preço cai
            long sold  = sellVolume.computeIfAbsent(commodity.id(), k -> new AtomicLong(0)).getAndSet(0);
            //    Normaliza por uma "base de referência" (ex: 100 itens = 1 unidade de pressão)
            double supplyPressure = -(sold / 100.0) * pressureFactor;
            //    Clamp para evitar crash total em dump massivo: máximo -15% por tick
            supplyPressure = Math.max(supplyPressure, -0.15);

            // 2. Mean-reversion: o preço é puxado suavemente de volta ao preço inicial
            //    Quando currentPrice > initial → drift negativo; quando < initial → positivo
            double reversionDrift = 0.0;
            if (initial.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = current.doubleValue() / initial.doubleValue();
                reversionDrift = (1.0 - ratio) * meanReversionRate;
            }

            // 3. Ruído aleatório (volatilidade da commodity)
            double noise = rand.nextGaussian() * commodity.volatility();

            // 4. Boost do setor econômico do evento ativo
            double sectorBoost = eventManager.getSectorBoost(commodity.sector());

            // 5. Calcula variação total e aplica
            double changePct = supplyPressure + reversionDrift + noise + sectorBoost;
            //    Limita variação máxima a ±20% por tick
            changePct = Math.max(-0.20, Math.min(0.20, changePct));

            double minPrice = initial.doubleValue() * minPriceRatio;
            double maxPrice = initial.doubleValue() * maxPriceRatio;
            double newRaw   = Math.max(minPrice, Math.min(maxPrice, current.doubleValue() * (1.0 + changePct)));

            BigDecimal newPrice = BigDecimal.valueOf(newRaw).setScale(2, RoundingMode.HALF_UP);
            currentPrices.put(commodity.id(), newPrice);

            // Persiste snapshot de preço
            repo.savePrice(new CommodityPrice(commodity.id(), newPrice, System.currentTimeMillis()));
        }

        // Limpeza de histórico antigo (baixa prioridade, já estamos em async)
        for (Commodity c : commodities.values()) {
            repo.purgeOldPrices(c.id(), historyKeep);
        }
    }

    // -------------------------------------------------------
    // Consultas assíncronas (para GUI e commands)
    // -------------------------------------------------------

    /** Busca histórico de preços de forma assíncrona, entrega resultado na main thread. */
    public void getPriceHistoryAsync(String commodityId, int limit, Consumer<List<CommodityPrice>> callback) {
        async.runAsync(() -> {
            List<CommodityPrice> history = repo.getHistory(commodityId, limit);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> callback.accept(history));
        });
    }

    // -------------------------------------------------------
    // Helpers de inventário (main thread)
    // -------------------------------------------------------

    /** Conta quantos itens de um material o jogador possui no inventário. */
    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** Remove {@code qty} itens do inventário do jogador. */
    private void removeItems(Player player, Material material, int qty) {
        int remaining = qty;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;

            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }
}
