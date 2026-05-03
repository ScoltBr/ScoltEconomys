package me.scoltbr.scoltEconomys.stock.gui;

import me.scoltbr.scoltEconomys.stock.*;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Monta e abre todos os menus do Mercado de Ações.
 *
 * Menus disponíveis:
 *  - MARKET        (/bolsa)              3 linhas
 *  - COMPANY_DETAIL (/bolsa info <id>)  4 linhas
 *  - PORTFOLIO     (/bolsa carteira)     varia
 *  - TOP_HOLDERS   (/bolsa top <id>)    3 linhas
 */
public final class StockMenuService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private static final NamespacedKey STOCK_ID_KEY =
            new NamespacedKey("scolteconomys", "stock_id");
    private static final NamespacedKey STOCK_ACTION_KEY =
            new NamespacedKey("scolteconomys", "stock_action");

    private final Plugin plugin;
    private final StockMarketService stockService;
    private final me.scoltbr.scoltEconomys.account.AccountService accountService;

    // Materiais por setor (mais "premium")
    private static final Map<String, Material> SECTOR_ICONS = Map.of(
            "bancario",      Material.GOLD_BLOCK,
            "comercio",      Material.EMERALD_BLOCK,
            "mineracao",     Material.NETHERITE_PICKAXE,
            "farmaceutico",  Material.BREWING_STAND,
            "tecnologia",    Material.NETHER_STAR,
            "agro",          Material.HAY_BLOCK,
            "energia",       Material.BEACON
    );

    public StockMenuService(Plugin plugin, StockMarketService stockService, me.scoltbr.scoltEconomys.account.AccountService accountService) {
        this.plugin = plugin;
        this.stockService = stockService;
        this.accountService = accountService;
    }

    // -------------------------------------------------------
    // MARKET — visão geral
    // -------------------------------------------------------

    /** Abre o menu principal da bolsa (dados em memória — sem IO). */
    public void openMarket(Player player) {
        Map<String, Stock> stocks = stockService.getStocks();
        int rows = Math.min(6, Math.max(3, (int) Math.ceil(stocks.size() / 7.0) + 2));

        Inventory inv = Bukkit.createInventory(
                new StockMenuHolder(StockMenuHolder.MenuType.MARKET, null),
                rows * 9,
                MM.deserialize("<gradient:#ffd700:#ff8c00><b><underlined>📈 BOLA DE VALORES</underlined></b></gradient>")
        );

        // Bordas laterais e fundo fundo informativo
        fillBorders(inv, rows);

        // Carrega holdings para mostrar P&L no menu principal
        stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int slot = 10; // Começa no slot 10 (segunda linha, segunda coluna)
                for (Stock stock : stocks.values()) {
                    if (slot % 9 == 8) slot += 2; // Pula as bordas
                    if (slot >= (rows - 1) * 9) break;

                    StockHolding h = holdings.get(stock.id());
                    inv.setItem(slot++, buildCompanyOverviewItem(stock, h));
                }

                // Botão "Meu Portfólio" centralizado embaixo
                inv.setItem(rows * 9 - 5, buildNavItem(Material.CHEST, "<gradient:#9b59b6:#3498db><b>💼 Minha Carteira</b></gradient>",
                        List.of("<gray>Veja o detalhamento dos seus investimentos", "<gray>e performance consolidada.", "", "<white>➲ Clique para abrir")));

                // Fechar (canto inferior direito) e Info (canto inferior esquerdo)
                inv.setItem(rows * 9 - 1, buildNavItem(Material.BARRIER, "<red><b>Fechar</b></red>", List.of()));
                inv.setItem(rows * 9 - 9, buildNavItem(Material.BOOK, "<yellow><b>Informações</b></yellow>",
                        List.of("<gray>O mercado oscila a cada hora.", "<gray>Fique atento aos eventos econômicos!", "", "<gray>Corretagem padrão: <white>1%")));

                player.openInventory(inv);
            });
        });
    }

    // -------------------------------------------------------
    // COMPANY DETAIL — gráfico + compra/venda (4 linhas)
    // -------------------------------------------------------

    /**
     * Abre o menu detalhado de uma empresa.
     * Busca histórico de preços de forma assíncrona antes de abrir.
     */
    public void openCompanyDetail(Player player, String stockId) {
        stockService.getPriceHistoryAsync(stockId, 9, history -> {
            Stock stock = stockService.getStock(stockId).orElse(null);
            if (stock == null) {
                MessageUtils.sendError(player, "Empresa não encontrada.");
                return;
            }

            Inventory inv = Bukkit.createInventory(
                    new StockMenuHolder(StockMenuHolder.MenuType.COMPANY_DETAIL, stockId),
                    45, // Aumentado para 5 linhas
                    MM.deserialize("<gradient:#00ffa1:#0099ff><b>📊 " + stripMM(stock.displayName()).toUpperCase() + "</b></gradient>")
            );

            // Moldura Premium
            fillBorders(inv, 5);

            // Slot 4: Info da empresa
            inv.setItem(4, buildCompanyDetailHeader(stock));

            // Slot 13: Saldo do Jogador
            inv.setItem(13, buildUserBalanceItem(player));

            // Linha 2 (Slots 18-26): Gráfico de preços
            buildPriceChart(inv, 19, history, stockService.currentPrice(stockId));

            stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
                long myQty = 0;
                java.math.BigDecimal myAvg = java.math.BigDecimal.ZERO;
                StockHolding h = holdings.get(stockId);
                if (h != null) { myQty = h.quantity(); myAvg = h.avgPrice(); }

                final long finalQty = myQty;
                final java.math.BigDecimal finalAvg = myAvg;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Slot 31 = info da posição atual
                    inv.setItem(31, buildHoldingInfoItem(stock, finalQty, finalAvg));

                    java.math.BigDecimal walletBalance = accountService.getWalletSync(player.getUniqueId());

                    // Botões de compra (Verde)
                    long avail = stockService.availableShares(stockId);
                    inv.setItem(27, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 1</b></green>",  stockId, 1,    avail, "BUY", walletBalance));
                    inv.setItem(28, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 10</b></green>", stockId, 10,   avail, "BUY", walletBalance));
                    inv.setItem(29, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 100</b></green>",stockId, 100,  avail, "BUY", walletBalance));
                    inv.setItem(30, buildActionButton(Material.LIME_STAINED_GLASS,              "<green><b>Comprar Máximo</b></green>",stockId, -1,   avail, "BUY_MAX", walletBalance));

                    // Botões de venda (Vermelho)
                    long myHeld = finalQty;
                    inv.setItem(32, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 1</b></red>",   stockId, 1,   myHeld, "SELL", walletBalance));
                    inv.setItem(33, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 10</b></red>",  stockId, 10,  myHeld, "SELL", walletBalance));
                    inv.setItem(34, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 100</b></red>", stockId, 100, myHeld, "SELL", walletBalance));
                    inv.setItem(35, buildActionButton(Material.RED_STAINED_GLASS,              "<red><b>Vender Tudo</b></red>", stockId, -1,  myHeld, "SELL_ALL", walletBalance));

                    // Navegação e Top
                    inv.setItem(40, buildNavItem(Material.ARROW, "<yellow><b>Voltar para a Bolsa</b></yellow>", List.of("<gray>Retorna ao mercado global")));
                    inv.setItem(22, buildNavItem(Material.PLAYER_HEAD, "<gold><b>Maiores Acionistas</b></gold>", List.of("<gray>Ver ranking de investidores", "<gray>desta empresa.")));
                });
            });

            player.openInventory(inv);
        });
    }

    // -------------------------------------------------------
    // PORTFOLIO
    // -------------------------------------------------------

    public void openPortfolio(Player player) {
        stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
            int rows = Math.min(6, Math.max(4, (int) Math.ceil(holdings.size() / 7.0) + 2));

            Inventory inv = Bukkit.createInventory(
                    new StockMenuHolder(StockMenuHolder.MenuType.PORTFOLIO, null),
                    rows * 9,
                    MM.deserialize("<gradient:#9b59b6:#3498db><b>💼 MINHA CARTEIRA</b></gradient>")
            );

            fillBorders(inv, rows);

            // Resumo da Carteira (Slot 4)
            inv.setItem(4, buildPortfolioSummaryItem(holdings));

            if (holdings.isEmpty()) {
                inv.setItem(22, buildNavItem(Material.PAPER, "<gray>Você ainda não possui investimentos</gray>",
                                List.of("<gray>Acesse o mercado global para", "<gray>começar a investir.", "", "<white>➲ Clique em Voltar")));
            } else {
                int slot = 10;
                for (StockHolding holding : holdings.values()) {
                    if (slot % 9 == 8) slot += 2;
                    if (slot >= (rows - 1) * 9) break;
                    inv.setItem(slot++, buildPortfolioItem(holding));
                }
            }

            inv.setItem(rows * 9 - 5,
                    buildNavItem(Material.ARROW, "<yellow><b>Voltar para a Bolsa</b></yellow>", List.of("<gray>Retorna ao mercado global")));

            player.openInventory(inv);
        });
    }

    // -------------------------------------------------------
    // TOP HOLDERS
    // -------------------------------------------------------

    public void openTopHolders(Player player, String stockId) {
        stockService.getTopHoldersAsync(stockId, 10, holders -> {
            Stock stock = stockService.getStock(stockId).orElse(null);
            String title = stock != null ? stripMM(stock.displayName()) : stockId;

            Inventory inv = Bukkit.createInventory(
                    new StockMenuHolder(StockMenuHolder.MenuType.TOP_HOLDERS, stockId),
                    27,
                    MM.deserialize("<gold><b>🏆 Top Acionistas — " + title + "</b></gold>")
            );

            fillRow(inv, 2, buildBorder());

            if (holders.isEmpty()) {
                inv.setItem(13, buildNavItem(Material.PAPER, "<gray>Nenhum acionista ainda</gray>", List.of()));
            } else {
                for (int i = 0; i < Math.min(holders.size(), 9); i++) {
                    StockHolding h = holders.get(i);
                    String name = Bukkit.getOfflinePlayer(h.uuid()).getName();
                    if (name == null) name = h.uuid().toString().substring(0, 8);

                    ItemStack item = buildSimpleItem(rankMaterial(i),
                            rankColor(i) + "<b>#" + (i + 1) + " " + name + "</b>",
                            List.of(
                                "<gray>Ações: <white>" + h.quantity(),
                                "<gray>Preço médio: <yellow>$" + MoneyFormat.format(h.avgPrice())
                            ));
                    inv.setItem(i, item);
                }
            }

            inv.setItem(18, buildNavItem(Material.ARROW, "<yellow>Voltar</yellow>", List.of()));
            player.openInventory(inv);
        });
    }

    // -------------------------------------------------------
    // Construtores de itens
    // -------------------------------------------------------

    private ItemStack buildCompanyOverviewItem(Stock s, StockHolding h) {
        java.math.BigDecimal price = stockService.currentPrice(s.id());
        long available = stockService.availableShares(s.id());
        double usedPct = (1.0 - (double) available / s.totalShares()) * 100.0;

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Setor: <white>" + s.sector().substring(0, 1).toUpperCase() + s.sector().substring(1));
        lore.add("<gray>Preço: <green>$" + MoneyFormat.format(price));
        lore.add("<gray>Oferta: <yellow>" + available + " <dark_gray>(" + String.format("%.1f", 100 - usedPct) + "% livre)");

        if (h != null && h.quantity() > 0) {
            java.math.BigDecimal curVal = price.multiply(java.math.BigDecimal.valueOf(h.quantity()));
            java.math.BigDecimal pnl = price.subtract(h.avgPrice()).multiply(java.math.BigDecimal.valueOf(h.quantity()));
            String pnlColor = pnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";
            lore.add("");
            lore.add("<gold>Sua Posição:");
            lore.add(" <gray>• Quantidade: <white>" + h.quantity());
            lore.add(" <gray>• P&L: " + pnlColor + "$" + MoneyFormat.format(pnl));
        }

        lore.add("");
        lore.add("<aqua>➞ Clique para negociar");

        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);
        ItemStack item = buildSimpleItem(mat, "<gradient:#ffffff:#bbbbbb><b>" + s.displayName() + "</b></gradient>", lore);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(STOCK_ID_KEY, PersistentDataType.STRING, s.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildCompanyDetailHeader(Stock s) {
        java.math.BigDecimal price = stockService.currentPrice(s.id());
        long available = stockService.availableShares(s.id());
        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);
        return buildSimpleItem(mat, "<gradient:#ffff55:#ffd700><b>" + stripMM(s.displayName()).toUpperCase() + "</b></gradient>", List.of(
                "<gray>Setor: <white>" + s.sector(),
                "<gray>Preço atual: <green>$" + MoneyFormat.format(price),
                "<gray>Oferta total: <yellow>" + s.totalShares(),
                "<gray>Disponíveis: <yellow>" + available,
                "<gray>Corretagem: <white>" + String.format("%.1f%%", s.brokerageFee().doubleValue() * 100),
                "",
                "<gray><i>Valores atualizados em tempo real</i>"
        ));
    }

    private void buildPriceChart(Inventory inv, int startSlot, List<StockPrice> history, java.math.BigDecimal currentPrice) {
        for (int i = 0; i < 7; i++) inv.setItem(startSlot + i, buildChartPane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Processando dados...", List.of()));

        if (history.isEmpty()) return;

        List<StockPrice> view = history.size() > 6 ? history.subList(history.size() - 6, history.size()) : new ArrayList<>(history);
        view.add(new StockPrice("now", currentPrice, System.currentTimeMillis()));

        for (int i = 0; i < view.size(); i++) {
            if (i >= 7) break;
            StockPrice point = view.get(i);
            java.math.BigDecimal prev = i == 0 ? point.price() : view.get(i - 1).price();
            java.math.BigDecimal change = point.price().subtract(prev);
            double changePct = prev.compareTo(java.math.BigDecimal.ZERO) > 0
                    ? change.divide(prev, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0 : 0;

            Material mat;
            String color;
            if (i == 0 || Math.abs(changePct) < 0.001) {
                mat = Material.YELLOW_STAINED_GLASS_PANE; color = "<yellow>";
            } else if (change.compareTo(java.math.BigDecimal.ZERO) > 0) {
                mat = Material.LIME_STAINED_GLASS_PANE; color = "<green>";
            } else {
                mat = Material.RED_STAINED_GLASS_PANE; color = "<red>";
            }

            String timeLabel = i < view.size() - 1 ? TIME_FMT.format(Instant.ofEpochMilli(point.recordedAt())) : "AGORA";
            String changeLine = i == 0 ? "<gray>Referência" : color + "Variação: " + String.format("%+.2f%%", changePct);

            inv.setItem(startSlot + i, buildChartPane(mat,
                    color + "<b>$" + MoneyFormat.format(point.price()) + "</b>",
                    List.of("<gray>Horário: <white>" + timeLabel, changeLine)));
        }
    }

    private ItemStack buildHoldingInfoItem(Stock s, long qty, java.math.BigDecimal avgPrice) {
        java.math.BigDecimal cur = stockService.currentPrice(s.id());
        if (qty == 0) {
            return buildSimpleItem(Material.PAPER, "<gray>Você não possui ações desta empresa", List.of(
                    "<gray>Use os botões abaixo para comprar"
            ));
        }
        java.math.BigDecimal pnl = cur.subtract(avgPrice).multiply(java.math.BigDecimal.valueOf(qty));
        double pnlPct = avgPrice.compareTo(java.math.BigDecimal.ZERO) > 0
                ? cur.subtract(avgPrice).divide(avgPrice, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0 : 0;
        String pnlColor = pnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";

        return buildSimpleItem(Material.GOLD_NUGGET, "<yellow><b>Sua Posição</b></yellow>", List.of(
                "<gray>Ações: <white>" + qty,
                "<gray>Preço médio: <yellow>$" + MoneyFormat.format(avgPrice),
                "<gray>Valor atual: <white>$" + MoneyFormat.format(cur.multiply(java.math.BigDecimal.valueOf(qty))),
                "<gray>P&L: " + pnlColor + "$" + MoneyFormat.format(pnl) +
                        " (" + String.format("%+.2f%%", pnlPct) + ")"
        ));
    }

    private ItemStack buildPortfolioItem(StockHolding h) {
        Stock s = stockService.getStock(h.stockId()).orElse(null);
        if (s == null) return buildSimpleItem(Material.PAPER, "<gray>" + h.stockId(), List.of());

        java.math.BigDecimal cur = stockService.currentPrice(s.id());
        java.math.BigDecimal pnl = h.unrealizedPnl(cur);
        double pnlPct = h.pnlPercent(cur);
        String pnlColor = pnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";
        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);

        ItemStack item = buildSimpleItem(mat, s.displayName(), List.of(
                "<gray>Ações: <white>" + h.quantity(),
                "<gray>Preço médio: <yellow>$" + MoneyFormat.format(h.avgPrice()),
                "<gray>Preço atual: <white>$" + MoneyFormat.format(cur),
                "<gray>Valor total: <white>$" + MoneyFormat.format(cur.multiply(java.math.BigDecimal.valueOf(h.quantity()))),
                "<gray>P&L: " + pnlColor + "$" + MoneyFormat.format(pnl) +
                        " (" + String.format("%+.2f%%", pnlPct) + ")",
                "",
                "<aqua>Clique para abrir a empresa"
        ));

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(STOCK_ID_KEY, PersistentDataType.STRING, s.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildActionButton(Material mat, String name, String stockId, long qty, long available, String action, java.math.BigDecimal walletBalance) {
        java.math.BigDecimal price = stockService.currentPrice(stockId);
        Stock s = stockService.getStocks().get(stockId);

        long effectiveQty = qty;
        if (action.equals("BUY_MAX") && s != null) {
            java.math.BigDecimal priceWithFee = price.multiply(java.math.BigDecimal.ONE.add(s.brokerageFee()));
            if (priceWithFee.compareTo(java.math.BigDecimal.ZERO) > 0) {
                long maxAffordable = walletBalance.divide(priceWithFee, 0, java.math.RoundingMode.DOWN).longValue();
                effectiveQty = Math.min(maxAffordable, available);
            } else {
                effectiveQty = available;
            }
        } else if (qty < 0) {
            effectiveQty = available;
        }

        boolean canAfford = effectiveQty > 0;

        List<String> lore = new ArrayList<>();
        if (effectiveQty > 0 && s != null) {
            java.math.BigDecimal cost = price.multiply(java.math.BigDecimal.valueOf(effectiveQty));
            java.math.BigDecimal fee  = cost.multiply(s.brokerageFee());
            if (action.startsWith("BUY")) {
                lore.add("<gray>Subtotal: <white>$" + MoneyFormat.format(cost));
                lore.add("<gray>Corretagem (" + String.format("%.0f%%", s.brokerageFee().doubleValue() * 100) + "): <yellow>$" + MoneyFormat.format(fee));
                lore.add("<gray>Total: <green><b>$" + MoneyFormat.format(cost.add(fee)) + "</b></green>");
                lore.add("");
                lore.add("<gray>Lote: <white>" + effectiveQty);
            } else {
                lore.add("<gray>Valor Bruto: <white>$" + MoneyFormat.format(price.multiply(java.math.BigDecimal.valueOf(effectiveQty))));
                lore.add("<gray>Corretagem: <yellow>$" + MoneyFormat.format(fee));
                lore.add("<gray>Recebimento Líquido: <green><b>$" + MoneyFormat.format(cost.subtract(fee)) + "</b></green>");
                lore.add("");
                lore.add("<gray>Lote: <white>" + (qty < 0 ? "TUDO" : qty));
            }
        }
        if (effectiveQty <= 0) {
            lore.add("<red>" + (action.startsWith("BUY") ? "Saldo ou ações insuficientes" : "Quantidade insuficiente"));
            mat = Material.BARRIER;
        }

        ItemStack item = buildSimpleItem(mat, name, lore);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(STOCK_ACTION_KEY, PersistentDataType.STRING, action + ":" + stockId + ":" + effectiveQty);
            item.setItemMeta(meta);
        }
        return item;
    }

    private java.math.BigDecimal proceeds(java.math.BigDecimal price, long qty) { return price.multiply(java.math.BigDecimal.valueOf(qty)); }

    private ItemStack buildUserBalanceItem(Player player) {
        java.math.BigDecimal balance = accountService.getWalletSync(player.getUniqueId());
        return buildSimpleItem(Material.PLAYER_HEAD, "<gold><b>Seu Saldo Disponível</b></gold>",
                List.of("<gray>Total em mãos: <green>$" + MoneyFormat.format(balance),
                        "",
                        "<gray><i>Você pode usar este saldo para</i>",
                        "<gray><i>comprar novas ações.</i>"));
    }

    private ItemStack buildPortfolioSummaryItem(Map<String, StockHolding> holdings) {
        java.math.BigDecimal totalInvested = java.math.BigDecimal.ZERO;
        java.math.BigDecimal currentMarketValue = java.math.BigDecimal.ZERO;

        for (StockHolding h : holdings.values()) {
            java.math.BigDecimal cur = stockService.currentPrice(h.stockId());
            totalInvested = totalInvested.add(h.avgPrice().multiply(java.math.BigDecimal.valueOf(h.quantity())));
            currentMarketValue = currentMarketValue.add(cur.multiply(java.math.BigDecimal.valueOf(h.quantity())));
        }

        java.math.BigDecimal totalPnl = currentMarketValue.subtract(totalInvested);
        double pnlPct = totalInvested.compareTo(java.math.BigDecimal.ZERO) > 0
                ? totalPnl.divide(totalInvested, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0 : 0;
        String pnlColor = totalPnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";

        return buildSimpleItem(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "<gradient:#9b59b6:#3498db><b>📊 PERFORMANCE GLOBAL</b></gradient>", List.of(
                "<gray>Total Investido: <white>$" + MoneyFormat.format(totalInvested),
                "<gray>Valor de Mercado: <white>$" + MoneyFormat.format(currentMarketValue),
                "<gray>Lucro/Prejuízo: " + pnlColor + "<b>$" + MoneyFormat.format(totalPnl) + " (" + String.format("%+.2f%%", pnlPct) + ")</b>",
                "",
                "<gray><i>Patrimônio alocado em " + holdings.size() + " ativo(s)</i>"
        ));
    }

    // -------------------------------------------------------
    // Helpers de item
    // -------------------------------------------------------

    private ItemStack buildSimpleItem(Material mat, String name, List<String> loreDef) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MM.deserialize("<!italic>" + name));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : loreDef) {
            loreComponents.add(MM.deserialize("<!italic>" + line));
        }
        meta.lore(loreComponents);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildChartPane(Material mat, String name, List<String> lore) {
        return buildSimpleItem(mat, name, lore);
    }

    private ItemStack buildNavItem(Material mat, String name, List<String> lore) {
        return buildSimpleItem(mat, name, lore);
    }

    private ItemStack buildBorder() {
        return buildSimpleItem(Material.BLACK_STAINED_GLASS_PANE, "<black> </black>", List.of());
    }

    private void fillBorders(Inventory inv, int rows) {
        ItemStack border = buildBorder();
        // Linha superior
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        // Linha inferior
        for (int i = (rows - 1) * 9; i < rows * 9; i++) inv.setItem(i, border);
        // Laterais
        for (int i = 1; i < rows - 1; i++) {
            inv.setItem(i * 9, border);
            inv.setItem(i * 9 + 8, border);
        }
    }

    private void fillRow(Inventory inv, int row, ItemStack item) {
        int start = row * 9;
        for (int i = start; i < start + 9; i++) inv.setItem(i, item);
    }

    private String stripMM(String mmText) {
        return MM.stripTags(mmText);
    }

    private Material rankMaterial(int pos) {
        return switch (pos) {
            case 0 -> Material.GOLD_BLOCK;
            case 1 -> Material.IRON_BLOCK;
            case 2 -> Material.COPPER_BLOCK;
            default -> Material.PAPER;
        };
    }

    private String rankColor(int pos) {
        return switch (pos) {
            case 0 -> "<gold>";
            case 1 -> "<gray>";
            case 2 -> "<#cd7f32>";
            default -> "<white>";
        };
    }
}
