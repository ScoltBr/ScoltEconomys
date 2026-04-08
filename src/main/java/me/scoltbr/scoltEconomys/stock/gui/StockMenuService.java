package me.scoltbr.scoltEconomys.stock.gui;

import me.scoltbr.scoltEconomys.stock.*;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    private final Plugin plugin;
    private final StockMarketService stockService;

    // Materiais por setor
    private static final Map<String, Material> SECTOR_ICONS = Map.of(
            "bancario",      Material.GOLD_INGOT,
            "comercio",      Material.EMERALD,
            "mineracao",     Material.IRON_PICKAXE,
            "farmaceutico",  Material.HONEY_BOTTLE,
            "tecnologia",    Material.COMPARATOR,
            "agro",          Material.WHEAT,
            "energia",       Material.BLAZE_POWDER
    );

    public StockMenuService(Plugin plugin, StockMarketService stockService) {
        this.plugin = plugin;
        this.stockService = stockService;
    }

    // -------------------------------------------------------
    // MARKET — visão geral
    // -------------------------------------------------------

    /** Abre o menu principal da bolsa (dados em memória — sem IO). */
    public void openMarket(Player player) {
        Map<String, Stock> stocks = stockService.getStocks();
        int rows = Math.max(2, (int) Math.ceil(stocks.size() / 9.0) + 1);
        rows = Math.min(rows, 4);

        Inventory inv = Bukkit.createInventory(
                new StockMenuHolder(StockMenuHolder.MenuType.MARKET, null),
                rows * 9,
                MM.deserialize("<gradient:#ffd700:#ff8c00><b>📈 Bolsa de Valores</b></gradient>")
        );

        // Fundo decorativo
        ItemStack border = buildBorder();
        for (int i = (rows - 1) * 9; i < rows * 9; i++) inv.setItem(i, border);

        // Empresas — uma por slot
        int slot = 0;
        for (Stock stock : stocks.values()) {
            if (slot >= (rows - 1) * 9) break;
            inv.setItem(slot++, buildCompanyOverviewItem(stock));
        }

        // Botão "Meu Portfólio" no canto inferior esquerdo
        inv.setItem((rows - 1) * 9, buildNavItem(Material.CHEST, "<green><b>Meu Portfólio</b></green>",
                List.of("<gray>Clique para ver suas ações e P&L")));

        // Fechar no canto inferior direito
        inv.setItem(rows * 9 - 1, buildNavItem(Material.BARRIER, "<red>Fechar</red>", List.of()));

        player.openInventory(inv);
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
                    36,
                    MM.deserialize("<gradient:#00ffa1:#0099ff><b>📊 " + stripMM(stock.displayName()) + "</b></gradient>")
            );

            // Linha 0: bordas + info da empresa (slot 4)
            fillRow(inv, 0, buildBorder());
            inv.setItem(4, buildCompanyDetailHeader(stock));

            // Linha 1: gráfico de preços (9 slots)
            buildPriceChart(inv, 9, history, stockService.currentPrice(stockId));

            // Linha 2: separador + info de posição (slot 18)
            fillRow(inv, 2, buildBorder());
            stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
                long myQty = 0;
                double myAvg = 0;
                StockHolding h = holdings.get(stockId);
                if (h != null) { myQty = h.quantity(); myAvg = h.avgPrice(); }

                // Slot 22 = info da posição atual
                final long finalQty = myQty;
                final double finalAvg = myAvg;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inv.setItem(22, buildHoldingInfoItem(stock, finalQty, finalAvg));

                    // Linha 3: botões de compra/venda
                    long avail = stockService.availableShares(stockId);
                    inv.setItem(27, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 1</b></green>",  stockId, 1,    avail, "BUY"));
                    inv.setItem(28, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 10</b></green>", stockId, 10,   avail, "BUY"));
                    inv.setItem(29, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 100</b></green>",stockId, 100,  avail, "BUY"));
                    inv.setItem(30, buildActionButton(Material.LIME_BLOCK,              "<green><b>Comprar Máx</b></green>",stockId, -1,   avail, "BUY_MAX"));

                    long myHeld = finalQty;
                    inv.setItem(32, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 1</b></red>",   stockId, 1,   myHeld, "SELL"));
                    inv.setItem(33, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 10</b></red>",  stockId, 10,  myHeld, "SELL"));
                    inv.setItem(34, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 100</b></red>", stockId, 100, myHeld, "SELL"));
                    inv.setItem(35, buildActionButton(Material.RED_BLOCK,              "<red><b>Vender Tudo</b></red>", stockId, -1,  myHeld, "SELL_ALL"));

                    // Botão de voltar (slot 31) e top holders (slot 26)
                    inv.setItem(31, buildNavItem(Material.ARROW, "<yellow>Voltar</yellow>", List.of("<gray>Retorna à bolsa")));
                    inv.setItem(26, buildNavItem(Material.PLAYER_HEAD, "<gold>Top Acionistas</gold>", List.of("<gray>Ver maiores investidores")));
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
            int rows = Math.max(2, (int) Math.ceil(holdings.size() / 9.0) + 1);
            rows = Math.min(rows, 5);

            Inventory inv = Bukkit.createInventory(
                    new StockMenuHolder(StockMenuHolder.MenuType.PORTFOLIO, null),
                    rows * 9,
                    MM.deserialize("<gradient:#9b59b6:#3498db><b>💼 Meu Portfólio</b></gradient>")
            );

            // Fundo última linha
            fillRow(inv, rows - 1, buildBorder());

            if (holdings.isEmpty()) {
                inv.setItem(4 + (rows / 2) * 9,
                        buildNavItem(Material.PAPER, "<gray>Nenhuma ação em carteira</gray>",
                                List.of("<gray>Use /bolsa para comprar ações")));
            } else {
                int slot = 0;
                for (StockHolding holding : holdings.values()) {
                    if (slot >= (rows - 1) * 9) break;
                    inv.setItem(slot++, buildPortfolioItem(holding));
                }
            }

            inv.setItem((rows - 1) * 9,
                    buildNavItem(Material.ARROW, "<yellow>Voltar</yellow>", List.of("<gray>Retorna à bolsa")));

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

    private ItemStack buildCompanyOverviewItem(Stock s) {
        double price = stockService.currentPrice(s.id());
        long available = stockService.availableShares(s.id());
        double usedPct = (1.0 - (double) available / s.totalShares()) * 100.0;

        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);
        return buildSimpleItem(mat, s.displayName(), List.of(
                "<gray>Setor: <white>" + s.sector(),
                "<gray>Preço atual: <green>$" + MoneyFormat.format(price),
                "<gray>Disponíveis: <yellow>" + available + " <dark_gray>(" + String.format("%.1f", 100 - usedPct) + "% livre)",
                "",
                "<aqua>Clique para mais detalhes"
        ));
    }

    private ItemStack buildCompanyDetailHeader(Stock s) {
        double price = stockService.currentPrice(s.id());
        long available = stockService.availableShares(s.id());
        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);
        return buildSimpleItem(mat, s.displayName(), List.of(
                "<gray>Setor: <white>" + s.sector(),
                "<gray>Preço atual: <green>$" + MoneyFormat.format(price),
                "<gray>Oferta total: <yellow>" + s.totalShares(),
                "<gray>Disponíveis: <yellow>" + available,
                "<gray>Corretagem: <white>" + String.format("%.1f%%", s.brokerageFee() * 100)
        ));
    }

    private void buildPriceChart(Inventory inv, int startSlot, List<StockPrice> history, double currentPrice) {
        // Preenche com cinza (sem dados) e vai substituindo
        for (int i = 0; i < 9; i++) inv.setItem(startSlot + i, buildChartPane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Sem dados", List.of()));

        if (history.isEmpty()) return;

        // Mostramos os últimos 9 pontos do histórico
        List<StockPrice> view = history.size() > 9 ? history.subList(history.size() - 9, history.size()) : new ArrayList<>(history);

        // Adiciona ponto atual
        view.add(new StockPrice("now", currentPrice, System.currentTimeMillis()));
        if (view.size() > 9) view = view.subList(view.size() - 9, view.size());

        double basePrice = view.get(0).price();

        for (int i = 0; i < view.size(); i++) {
            StockPrice point = view.get(i);
            double prev = i == 0 ? point.price() : view.get(i - 1).price();
            double change = point.price() - prev;
            double changePct = prev > 0 ? (change / prev) * 100.0 : 0;

            Material mat;
            String color;
            String arrow;
            if (i == 0 || Math.abs(changePct) < 0.001) {
                mat = Material.YELLOW_STAINED_GLASS_PANE;
                color = "<yellow>"; arrow = "─";
            } else if (change > 0) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                color = "<green>"; arrow = "▲";
            } else {
                mat = Material.RED_STAINED_GLASS_PANE;
                color = "<red>"; arrow = "▼";
            }

            String timeLabel = i < view.size() - 1
                    ? TIME_FMT.format(Instant.ofEpochMilli(point.recordedAt()))
                    : "Agora";

            String changeLine = i == 0
                    ? "<gray>Ponto inicial"
                    : color + arrow + " " + String.format("%+.2f%%", changePct);

            inv.setItem(startSlot + i, buildChartPane(mat,
                    color + "<b>" + arrow + " $" + MoneyFormat.format(point.price()) + "</b>",
                    List.of("<gray>" + timeLabel, changeLine)
            ));
        }
    }

    private ItemStack buildHoldingInfoItem(Stock s, long qty, double avgPrice) {
        double cur = stockService.currentPrice(s.id());
        if (qty == 0) {
            return buildSimpleItem(Material.PAPER, "<gray>Você não possui ações desta empresa", List.of(
                    "<gray>Use os botões abaixo para comprar"
            ));
        }
        double pnl    = (cur - avgPrice) * qty;
        double pnlPct = avgPrice > 0 ? ((cur - avgPrice) / avgPrice) * 100.0 : 0;
        String pnlColor = pnl >= 0 ? "<green>" : "<red>";

        return buildSimpleItem(Material.GOLD_NUGGET, "<yellow><b>Sua Posição</b></yellow>", List.of(
                "<gray>Ações: <white>" + qty,
                "<gray>Preço médio: <yellow>$" + MoneyFormat.format(avgPrice),
                "<gray>Valor atual: <white>$" + MoneyFormat.format(cur * qty),
                "<gray>P&L: " + pnlColor + "$" + MoneyFormat.format(pnl) +
                        " (" + String.format("%+.2f%%", pnlPct) + ")"
        ));
    }

    private ItemStack buildPortfolioItem(StockHolding h) {
        Stock s = stockService.getStock(h.stockId()).orElse(null);
        if (s == null) return buildSimpleItem(Material.PAPER, "<gray>" + h.stockId(), List.of());

        double cur    = stockService.currentPrice(s.id());
        double pnl    = h.unrealizedPnl(cur);
        double pnlPct = h.pnlPercent(cur);
        String pnlColor = pnl >= 0 ? "<green>" : "<red>";
        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);

        return buildSimpleItem(mat, s.displayName(), List.of(
                "<gray>Ações: <white>" + h.quantity(),
                "<gray>Preço médio: <yellow>$" + MoneyFormat.format(h.avgPrice()),
                "<gray>Preço atual: <white>$" + MoneyFormat.format(cur),
                "<gray>Valor total: <white>$" + MoneyFormat.format(cur * h.quantity()),
                "<gray>P&L: " + pnlColor + "$" + MoneyFormat.format(pnl) +
                        " (" + String.format("%+.2f%%", pnlPct) + ")",
                "",
                "<aqua>Clique para abrir a empresa"
        ));
    }

    private ItemStack buildActionButton(Material mat, String name, String stockId, long qty, long available, String action) {
        double price = stockService.currentPrice(stockId);
        long effectiveQty = qty < 0 ? available : qty;   // -1 = máx

        boolean canAfford = effectiveQty > 0 && effectiveQty <= available;

        List<String> lore = new ArrayList<>();
        if (effectiveQty > 0) {
            double cost = price * effectiveQty;
            double fee  = cost * stockService.getStocks().get(stockId).brokerageFee();
            if (action.startsWith("BUY")) {
                lore.add("<gray>Custo: <white>$" + MoneyFormat.format(cost + fee));
                lore.add("<gray>Corretagem: <yellow>$" + MoneyFormat.format(fee));
                lore.add("<gray>Disponíveis: <yellow>" + available);
            } else {
                lore.add("<gray>Recebe: <white>$" + MoneyFormat.format(cost - fee));
                lore.add("<gray>Corretagem: <yellow>$" + MoneyFormat.format(fee));
                lore.add("<gray>Em carteira: <yellow>" + available);
            }
        }
        if (!canAfford) {
            lore.add("<red>Quantidade insuficiente");
            lore.add("<dark_red>Operação indisponível");
            mat = action.startsWith("BUY") ? Material.GLASS_PANE : Material.GLASS_PANE;
        }

        ItemStack item = buildSimpleItem(mat, name, lore);

        // Armazena metadados na lore final para o listener identificar a ação
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(Bukkit.getPluginManager().getPlugin("ScoltEconomys"), "stock_action"),
                    org.bukkit.persistence.PersistentDataType.STRING,
                    action + ":" + stockId + ":" + effectiveQty
            );
            item.setItemMeta(meta);
        }
        return item;
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
