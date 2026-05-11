package me.scoltbr.scoltEconomys.stock.gui;

import me.scoltbr.scoltEconomys.stock.*;
import me.scoltbr.scoltEconomys.util.ItemBuilder;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Monta e abre todos os menus do Mercado de Ações.
 */
public final class StockMenuService {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public static final NamespacedKey STOCK_ID_KEY =
            new NamespacedKey("scolteconomys", "stock_id");
    public static final NamespacedKey STOCK_ACTION_KEY =
            new NamespacedKey("scolteconomys", "stock_action");

    private final Plugin plugin;
    private final StockMarketService stockService;
    private final me.scoltbr.scoltEconomys.account.AccountService accountService;
    private final Map<UUID, BukkitRunnable> liveTasks = new HashMap<>();

    // Texturas Base64 para botões de navegação
    private static final String TEX_NEXT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWE0ZjY4YzhmYjI3OWU1MGFiNzg2ZjlmYTU0Yzg4Y2E0ZWNmZTFlYjVmZDVmMGMzOGM1NGM5YjFjNzIwM2Q3YSJ9fX0=";
    private static final String TEX_PREV = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGFlYzg1OWMxN2U4M2E4MTlhNGQ2YjJkZmUyYzNhMjQ2NWUyMjg3OGIyNmVlMjUzOTRjNDQ5OTgzNWNhNjA4YyJ9fX0=";
    private static final String TEX_CLOSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzNGY0ZjQ0NjRiNDdhZjJhNWQ0NGMyNGM2MGFjYmQ4ZWIyOGQzMjdjNWMxMWRmYWViYTIzMSJ9fX0=";
    private static final String TEX_INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjI3MzNlOTk2NGRmMmI4ZWYzYTI3NDY1MDZiMjFmMGFkNWNhZjFiOGMzODNlYTgyZGRkMjYyZjY3YmY5MjgyNSJ9fX0=";
    private static final String TEX_BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=";

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

    public void startLiveRefresh(Player player, StockMenuHolder.MenuType type, String stockId, int page) {
        cancelLiveRefresh(player);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof StockMenuHolder holder) {
                    if (holder.type() == type && Objects.equals(holder.stockId(), stockId)) {
                        // Faz uma atualização "silenciosa" sem reabrir
                        if (type == StockMenuHolder.MenuType.COMPANY_DETAIL) {
                            refreshCompanyDetailSilently(player, player.getOpenInventory().getTopInventory(), stockId);
                        } else if (type == StockMenuHolder.MenuType.MARKET) {
                            refreshMarketSilently(player, player.getOpenInventory().getTopInventory(), page);
                        }
                    } else {
                        cancel();
                    }
                } else {
                    cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 20L, 40L); // atualiza a cada 2 segundos
        liveTasks.put(player.getUniqueId(), task);
    }

    public void cancelLiveRefresh(Player player) {
        BukkitRunnable task = liveTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    // -------------------------------------------------------
    // MARKET
    // -------------------------------------------------------

    public void openMarket(Player player) {
        openMarket(player, 1);
    }

    public void openMarket(Player player, int page) {
        List<Stock> stocks = new ArrayList<>(stockService.getStocks().values());
        stocks.sort(Comparator.comparing(Stock::displayName)); // Ordem alfabética
        
        int itemsPerPage = 21; // 3 linhas centrais de 7 slots
        int maxPages = Math.max(1, (int) Math.ceil((double) stocks.size() / itemsPerPage));
        int actualPage = Math.max(1, Math.min(page, maxPages));

        Inventory inv = Bukkit.createInventory(
                new StockMenuHolder(StockMenuHolder.MenuType.MARKET, null, actualPage),
                54,
                MM.deserialize("<gradient:#ffd700:#ff8c00><b><underlined>📈 BOLA DE VALORES</underlined></b></gradient> <dark_gray>(" + actualPage + "/" + maxPages + ")")
        );

        fillBorders(inv, 6);

        // Preenche com placeholder de carregamento rápido
        for (int i = 0; i < itemsPerPage; i++) {
            inv.setItem(getSlot(i), ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name("<gray>Carregando...").build());
        }

        player.openInventory(inv);

        stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StockMenuHolder h)) return;
                if (h.type() != StockMenuHolder.MenuType.MARKET) return;
                
                renderMarketItems(inv, stocks, holdings, actualPage, maxPages);
                startLiveRefresh(player, StockMenuHolder.MenuType.MARKET, null, actualPage);
            });
        });
    }

    private void refreshMarketSilently(Player player, Inventory inv, int page) {
        List<Stock> stocks = new ArrayList<>(stockService.getStocks().values());
        stocks.sort(Comparator.comparing(Stock::displayName));
        int itemsPerPage = 21;
        int maxPages = Math.max(1, (int) Math.ceil((double) stocks.size() / itemsPerPage));
        
        stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StockMenuHolder h)) return;
                if (h.type() != StockMenuHolder.MenuType.MARKET) return;
                renderMarketItems(inv, stocks, holdings, page, maxPages);
            });
        });
    }

    private void renderMarketItems(Inventory inv, List<Stock> stocks, Map<String, StockHolding> holdings, int page, int maxPages) {
        int itemsPerPage = 21;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, stocks.size());

        for (int i = 0; i < itemsPerPage; i++) {
            int stockIndex = startIndex + i;
            if (stockIndex < endIndex) {
                Stock stock = stocks.get(stockIndex);
                StockHolding h = holdings.get(stock.id());
                inv.setItem(getSlot(i), buildCompanyOverviewItem(stock, h));
            } else {
                inv.setItem(getSlot(i), new ItemStack(Material.AIR));
            }
        }

        // Navegação Rodapé
        inv.setItem(45, ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_INFO).name("<yellow><b>Informações</b></yellow>").lore("<gray>O mercado oscila a cada hora.", "<gray>Fique atento aos eventos!", "", "<gray>Corretagem padrão: <white>1%").build());
        inv.setItem(49, setAction(ItemBuilder.of(Material.CHEST).name("<gradient:#9b59b6:#3498db><b>💼 Minha Carteira</b></gradient>").lore("<gray>Acesse seu portfólio.").build(), "OPEN_PORTFOLIO"));
        inv.setItem(53, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").build(), "CLOSE"));

        // Paginação
        if (page > 1) {
            inv.setItem(48, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_PREV).name("<yellow><b>Página Anterior</b></yellow>").build(), "PAGE_PREV"));
        } else {
            inv.setItem(48, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        if (page < maxPages) {
            inv.setItem(50, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_NEXT).name("<yellow><b>Próxima Página</b></yellow>").build(), "PAGE_NEXT"));
        } else {
            inv.setItem(50, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
    }

    private int getSlot(int index) {
        // Linhas 2, 3 e 4 (slots centrais 10-16, 19-25, 28-34)
        int row = index / 7;
        int col = index % 7;
        return (row + 1) * 9 + (col + 1);
    }

    // -------------------------------------------------------
    // COMPANY DETAIL
    // -------------------------------------------------------

    public void openCompanyDetail(Player player, String stockId) {
        Stock stock = stockService.getStock(stockId).orElse(null);
        if (stock == null) {
            MessageUtils.sendError(player, "Empresa não encontrada.");
            return;
        }

        Inventory inv = Bukkit.createInventory(
                new StockMenuHolder(StockMenuHolder.MenuType.COMPANY_DETAIL, stockId),
                54,
                MM.deserialize("<gradient:#00ffa1:#0099ff><b>📊 " + stripMM(stock.displayName()).toUpperCase() + "</b></gradient>")
        );

        fillBorders(inv, 6);
        player.openInventory(inv);

        refreshCompanyDetailSilently(player, inv, stockId);
        startLiveRefresh(player, StockMenuHolder.MenuType.COMPANY_DETAIL, stockId, 1);
    }

    private void refreshCompanyDetailSilently(Player player, Inventory inv, String stockId) {
        stockService.getPriceHistoryAsync(stockId, 9, history -> {
            Stock stock = stockService.getStock(stockId).orElse(null);
            if (stock == null) return;

            stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
                long myQty = 0;
                java.math.BigDecimal myAvg = java.math.BigDecimal.ZERO;
                StockHolding h = holdings.get(stockId);
                if (h != null) { myQty = h.quantity(); myAvg = h.avgPrice(); }

                final long finalQty = myQty;
                final java.math.BigDecimal finalAvg = myAvg;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StockMenuHolder holder)) return;
                    if (holder.type() != StockMenuHolder.MenuType.COMPANY_DETAIL || !Objects.equals(holder.stockId(), stockId)) return;

                    // Header Info
                    inv.setItem(4, buildCompanyDetailHeader(stock));
                    inv.setItem(13, buildUserBalanceItem(player));

                    // Gráfico de Preços (19-25)
                    buildPriceChart(inv, 19, history, stockService.currentPrice(stockId));

                    java.math.BigDecimal walletBalance = accountService.getWalletSync(player.getUniqueId());

                    // Info
                    inv.setItem(31, buildHoldingInfoItem(stock, finalQty, finalAvg));

                    // Botões Compra/Venda
                    long avail = stockService.availableShares(stockId);
                    inv.setItem(27, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 1</b></green>",  stockId, 1,    avail, "BUY", walletBalance));
                    inv.setItem(28, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 10</b></green>", stockId, 10,   avail, "BUY", walletBalance));
                    inv.setItem(29, buildActionButton(Material.LIME_STAINED_GLASS_PANE, "<green><b>Comprar 100</b></green>",stockId, 100,  avail, "BUY", walletBalance));
                    inv.setItem(30, buildActionButton(Material.LIME_STAINED_GLASS,       "<green><b>Comprar Máximo</b></green>",stockId, -1,   avail, "BUY_MAX", walletBalance));

                    inv.setItem(32, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 1</b></red>",   stockId, 1,   finalQty, "SELL", walletBalance));
                    inv.setItem(33, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 10</b></red>",  stockId, 10,  finalQty, "SELL", walletBalance));
                    inv.setItem(34, buildActionButton(Material.RED_STAINED_GLASS_PANE, "<red><b>Vender 100</b></red>", stockId, 100, finalQty, "SELL", walletBalance));
                    inv.setItem(35, buildActionButton(Material.RED_STAINED_GLASS,       "<red><b>Vender Tudo</b></red>", stockId, -1,  finalQty, "SELL_ALL", walletBalance));

                    // Navegação Inferior
                    inv.setItem(45, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_BACK).name("<yellow><b>Voltar</b></yellow>").build(), "BACK_TO_MARKET"));
                    inv.setItem(49, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_INFO).name("<gold><b>Maiores Acionistas</b></gold>").lore("<gray>Ver ranking desta empresa.").build(), "OPEN_TOP"));
                    inv.setItem(53, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").build(), "CLOSE"));
                });
            });
        });
    }

    // -------------------------------------------------------
    // PORTFOLIO
    // -------------------------------------------------------

    public void openPortfolio(Player player) {
        stockService.getPortfolioAsync(player.getUniqueId(), holdings -> {
            int rows = 6;
            Inventory inv = Bukkit.createInventory(
                    new StockMenuHolder(StockMenuHolder.MenuType.PORTFOLIO, null, 1),
                    rows * 9,
                    MM.deserialize("<gradient:#9b59b6:#3498db><b>💼 MINHA CARTEIRA</b></gradient>")
            );

            fillBorders(inv, rows);
            inv.setItem(4, buildPortfolioSummaryItem(holdings));

            if (holdings.isEmpty()) {
                inv.setItem(22, ItemBuilder.of(Material.PAPER).name("<gray>Você ainda não possui investimentos</gray>")
                        .lore("<gray>Acesse o mercado global para", "<gray>começar a investir.").build());
            } else {
                int slot = 10;
                for (StockHolding holding : holdings.values()) {
                    if (slot % 9 == 8) slot += 2;
                    if (slot >= (rows - 2) * 9) break; // limite temporario (fazer paginacao futura se carteira for gigante)
                    inv.setItem(slot++, buildPortfolioItem(holding));
                }
            }

            inv.setItem(45, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_BACK).name("<yellow><b>Voltar</b></yellow>").build(), "BACK_TO_MARKET"));
            inv.setItem(53, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").build(), "CLOSE"));

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
                    new StockMenuHolder(StockMenuHolder.MenuType.TOP_HOLDERS, stockId, 1),
                    36,
                    MM.deserialize("<gold><b>🏆 Top Acionistas — " + title + "</b></gold>")
            );

            fillBorders(inv, 4);

            if (holders.isEmpty()) {
                inv.setItem(13, ItemBuilder.of(Material.PAPER).name("<gray>Nenhum acionista ainda</gray>").build());
            } else {
                int slot = 10;
                for (int i = 0; i < Math.min(holders.size(), 7); i++) {
                    StockHolding h = holders.get(i);
                    String name = Bukkit.getOfflinePlayer(h.uuid()).getName();
                    if (name == null) name = h.uuid().toString().substring(0, 8);

                    ItemStack item = ItemBuilder.of(rankMaterial(i))
                            .name(rankColor(i) + "<b>#" + (i + 1) + " " + name + "</b>")
                            .lore("<gray>Ações: <white>" + h.quantity(), "<gray>Preço médio: <yellow>$" + MoneyFormat.format(h.avgPrice()))
                            .build();
                    inv.setItem(slot++, item);
                }
            }

            inv.setItem(27, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_BACK).name("<yellow><b>Voltar</b></yellow>").build(), "BACK_TO_DETAIL"));
            inv.setItem(35, setAction(ItemBuilder.of(Material.PLAYER_HEAD).texture(TEX_CLOSE).name("<red><b>Fechar</b></red>").build(), "CLOSE"));
            
            player.openInventory(inv);
        });
    }

    // -------------------------------------------------------
    // Construtores de Itens Dinâmicos
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
        ItemStack item = ItemBuilder.of(mat)
                .name("<gradient:#ffffff:#bbbbbb><b>" + s.displayName() + "</b></gradient>")
                .lore(lore)
                .build();

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
        return ItemBuilder.of(mat)
                .name("<gradient:#ffff55:#ffd700><b>" + stripMM(s.displayName()).toUpperCase() + "</b></gradient>")
                .lore(
                    "<gray>Setor: <white>" + s.sector(),
                    "<gray>Preço atual: <green>$" + MoneyFormat.format(price),
                    "<gray>Oferta total: <yellow>" + s.totalShares(),
                    "<gray>Disponíveis: <yellow>" + available,
                    "<gray>Corretagem: <white>" + String.format("%.1f%%", s.brokerageFee().doubleValue() * 100),
                    "",
                    "<gray><i>Valores atualizados em tempo real</i>"
                ).build();
    }

    private void buildPriceChart(Inventory inv, int startSlot, List<StockPrice> history, java.math.BigDecimal currentPrice) {
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

            inv.setItem(startSlot + i, ItemBuilder.of(mat)
                    .name(color + "<b>$" + MoneyFormat.format(point.price()) + "</b>")
                    .lore("<gray>Horário: <white>" + timeLabel, changeLine)
                    .build());
        }
    }

    private ItemStack buildHoldingInfoItem(Stock s, long qty, java.math.BigDecimal avgPrice) {
        java.math.BigDecimal cur = stockService.currentPrice(s.id());
        if (qty == 0) {
            return ItemBuilder.of(Material.PAPER).name("<gray>Você não possui ações desta empresa")
                    .lore("<gray>Use os botões abaixo para comprar").build();
        }
        java.math.BigDecimal pnl = cur.subtract(avgPrice).multiply(java.math.BigDecimal.valueOf(qty));
        double pnlPct = avgPrice.compareTo(java.math.BigDecimal.ZERO) > 0
                ? cur.subtract(avgPrice).divide(avgPrice, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100.0 : 0;
        String pnlColor = pnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";

        return ItemBuilder.of(Material.GOLD_NUGGET).name("<yellow><b>Sua Posição</b></yellow>")
                .lore(
                    "<gray>Ações: <white>" + qty,
                    "<gray>Preço médio: <yellow>$" + MoneyFormat.format(avgPrice),
                    "<gray>Valor atual: <white>$" + MoneyFormat.format(cur.multiply(java.math.BigDecimal.valueOf(qty))),
                    "<gray>P&L: " + pnlColor + "$" + MoneyFormat.format(pnl) + " (" + String.format("%+.2f%%", pnlPct) + ")"
                ).build();
    }

    private ItemStack buildPortfolioItem(StockHolding h) {
        Stock s = stockService.getStock(h.stockId()).orElse(null);
        if (s == null) return ItemBuilder.of(Material.PAPER).name("<gray>" + h.stockId()).build();

        java.math.BigDecimal cur = stockService.currentPrice(s.id());
        java.math.BigDecimal pnl = h.unrealizedPnl(cur);
        double pnlPct = h.pnlPercent(cur);
        String pnlColor = pnl.compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";
        Material mat = SECTOR_ICONS.getOrDefault(s.sector(), Material.PAPER);

        ItemStack item = ItemBuilder.of(mat).name("<gradient:#ffffff:#bbbbbb><b>" + s.displayName() + "</b></gradient>")
                .lore(
                    "<gray>Ações: <white>" + h.quantity(),
                    "<gray>Preço médio: <yellow>$" + MoneyFormat.format(h.avgPrice()),
                    "<gray>Preço atual: <white>$" + MoneyFormat.format(cur),
                    "<gray>Valor total: <white>$" + MoneyFormat.format(cur.multiply(java.math.BigDecimal.valueOf(h.quantity()))),
                    "<gray>P&L: " + pnlColor + "$" + MoneyFormat.format(pnl) + " (" + String.format("%+.2f%%", pnlPct) + ")",
                    "",
                    "<aqua>Clique para abrir a empresa"
                ).build();

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

        List<String> lore = new ArrayList<>();
        if (effectiveQty > 0 && s != null) {
            java.math.BigDecimal cost = price.multiply(java.math.BigDecimal.valueOf(effectiveQty));
            java.math.BigDecimal fee  = cost.multiply(s.brokerageFee());
            if (action.startsWith("BUY")) {
                lore.add("<gray>Subtotal: <white>$" + MoneyFormat.format(cost));
                lore.add("<gray>Corretagem: <yellow>$" + MoneyFormat.format(fee));
                lore.add("<gray>Total: <green><b>$" + MoneyFormat.format(cost.add(fee)) + "</b></green>");
                lore.add("");
                lore.add("<gray>Lote: <white>" + effectiveQty);
            } else {
                lore.add("<gray>Valor Bruto: <white>$" + MoneyFormat.format(cost));
                lore.add("<gray>Corretagem: <yellow>$" + MoneyFormat.format(fee));
                lore.add("<gray>Líquido: <green><b>$" + MoneyFormat.format(cost.subtract(fee)) + "</b></green>");
                lore.add("");
                lore.add("<gray>Lote: <white>" + (qty < 0 ? "TUDO" : qty));
            }
        }
        if (effectiveQty <= 0) {
            lore.add("<red>" + (action.startsWith("BUY") ? "Saldo ou ações insuficientes" : "Quantidade insuficiente"));
            mat = Material.BARRIER;
        }

        ItemStack item = ItemBuilder.of(mat).name(name).lore(lore).build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(STOCK_ACTION_KEY, PersistentDataType.STRING, action + ":" + stockId + ":" + effectiveQty);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildUserBalanceItem(Player player) {
        java.math.BigDecimal balance = accountService.getWalletSync(player.getUniqueId());
        return ItemBuilder.of(Material.GOLD_INGOT).name("<gold><b>Seu Saldo</b></gold>")
                .lore("<gray>Disponível: <green>$" + MoneyFormat.format(balance)).build();
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

        return ItemBuilder.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
                .name("<gradient:#9b59b6:#3498db><b>📊 PERFORMANCE GLOBAL</b></gradient>")
                .lore(
                    "<gray>Total Investido: <white>$" + MoneyFormat.format(totalInvested),
                    "<gray>Valor de Mercado: <white>$" + MoneyFormat.format(currentMarketValue),
                    "<gray>Lucro/Prejuízo: " + pnlColor + "<b>$" + MoneyFormat.format(totalPnl) + " (" + String.format("%+.2f%%", pnlPct) + ")</b>"
                ).build();
    }

    // -------------------------------------------------------
    // Design Helpers
    // -------------------------------------------------------

    private void fillBorders(Inventory inv, int rows) {
        ItemStack borderOuter = ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack borderInner = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

        for (int i = 0; i < rows * 9; i++) {
            int r = i / 9;
            int c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) {
                inv.setItem(i, borderOuter);
            } else if (r == 1 || r == rows - 2 || c == 1 || c == 7) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, borderInner); // profundidade
                }
            }
        }
    }

    private ItemStack setAction(ItemStack item, String action) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(STOCK_ACTION_KEY, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
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
