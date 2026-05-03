package me.scoltbr.scoltEconomys.stock.gui;

import me.scoltbr.scoltEconomys.stock.BuyResult;
import me.scoltbr.scoltEconomys.stock.SellResult;
import me.scoltbr.scoltEconomys.stock.StockMarketService;
import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Listener que intercepta cliques nos menus do Mercado de Ações.
 *
 * Responsabilidades:
 * - Rotear navegação entre menus (MARKET ↔ COMPANY_DETAIL ↔ PORTFOLIO ↔ TOP_HOLDERS)
 * - Executar operações de compra/venda via StockMarketService
 * - Exibir feedback visual ao jogador (mensagens + sons)
 */
public final class StockMenuListener implements Listener {

    private static final NamespacedKey STOCK_ID_KEY =
            new NamespacedKey("scolteconomys", "stock_id");
    private static final NamespacedKey STOCK_ACTION_KEY =
            new NamespacedKey("scolteconomys", "stock_action");

    private final Plugin plugin;
    private final StockMarketService stockService;
    private final StockMenuService menuService;

    public StockMenuListener(Plugin plugin, StockMarketService stockService, StockMenuService menuService) {
        this.plugin = plugin;
        this.stockService = stockService;
        this.menuService = menuService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof StockMenuHolder holder)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        // Som de clique padrão
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        switch (holder.type()) {
            case MARKET         -> handleMarketClick(player, clicked, meta);
            case COMPANY_DETAIL -> handleDetailClick(player, clicked, meta, holder);
            case PORTFOLIO      -> handlePortfolioClick(player, clicked, meta);
            case TOP_HOLDERS    -> handleTopHoldersClick(player, clicked, meta, holder);
        }
    }

    // -------------------------------------------------------
    // MARKET
    // -------------------------------------------------------

    private void handleMarketClick(Player player, ItemStack item, ItemMeta meta) {
        if (item.getType() == org.bukkit.Material.CHEST) {
            menuService.openPortfolio(player);
            return;
        }
        if (item.getType() == org.bukkit.Material.BARRIER) {
            player.closeInventory();
            return;
        }

        String stockId = meta.getPersistentDataContainer().get(STOCK_ID_KEY, PersistentDataType.STRING);
        if (stockId != null) {
            menuService.openCompanyDetail(player, stockId);
        }
    }

    // -------------------------------------------------------
    // COMPANY DETAIL
    // -------------------------------------------------------

    private void handleDetailClick(Player player, ItemStack item, ItemMeta meta, StockMenuHolder holder) {
        // Voltar para o mercado
        if (item.getType() == org.bukkit.Material.ARROW) {
            menuService.openMarket(player);
            return;
        }
        // Top acionistas
        if (item.getType() == org.bukkit.Material.PLAYER_HEAD) {
            menuService.openTopHolders(player, holder.stockId());
            return;
        }

        // Verifica se é um botão de compra/venda via PDC
        String rawAction = meta.getPersistentDataContainer().get(STOCK_ACTION_KEY, PersistentDataType.STRING);
        if (rawAction == null) return;

        // Formato: "ACTION:stockId:qty"
        String[] parts = rawAction.split(":");
        if (parts.length < 3) return;

        String action  = parts[0];
        String stockId = parts[1];
        long qty;
        try { qty = Long.parseLong(parts[2]); } catch (NumberFormatException e) { return; }

        switch (action) {
            case "BUY", "BUY_MAX" -> stockService.buyAsync(player.getUniqueId(), stockId, qty,
                    result -> onBuyResult(player, result, stockId));
            case "SELL" -> stockService.sellAsync(player.getUniqueId(), stockId, qty,
                    result -> onSellResult(player, result, stockId));
            case "SELL_ALL" -> {
                if (qty <= 0) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    MessageUtils.sendError(player, "Você não possui ações desta empresa.");
                    return;
                }
                stockService.sellAsync(player.getUniqueId(), stockId, -1,
                        result -> onSellResult(player, result, stockId));
            }
        }
    }

    // -------------------------------------------------------
    // PORTFOLIO
    // -------------------------------------------------------

    private void handlePortfolioClick(Player player, ItemStack item, ItemMeta meta) {
        if (item.getType() == org.bukkit.Material.ARROW) {
            menuService.openMarket(player);
            return;
        }
        
        String stockId = meta.getPersistentDataContainer().get(STOCK_ID_KEY, PersistentDataType.STRING);
        if (stockId != null) {
            menuService.openCompanyDetail(player, stockId);
        }
    }

    // -------------------------------------------------------
    // TOP HOLDERS
    // -------------------------------------------------------

    private void handleTopHoldersClick(Player player, ItemStack item, ItemMeta meta, StockMenuHolder holder) {
        if (item.getType() == org.bukkit.Material.ARROW) {
            menuService.openCompanyDetail(player, holder.stockId());
        }
    }

    // -------------------------------------------------------
    // Callbacks de resultado
    // -------------------------------------------------------

    private void onBuyResult(Player player, BuyResult result, String stockId) {
        if (!result.success()) {
            String msg = switch (result.reason()) {
                case "insufficient-funds"   -> "Saldo insuficiente na carteira.";
                case "insufficient-supply"  -> "Ações insuficientes disponíveis no mercado.";
                case "invalid-quantity"     -> "Quantidade inválida.";
                default -> "Erro ao processar compra: " + result.reason();
            };
            MessageUtils.sendError(player, msg);
            return;
        }

        MessageUtils.send(player,
                "<green>✔ Você comprou <white>" + result.qty() + " ação(ões)</white> de " +
                "<aqua>" + stockId + "</aqua> por <white>$" + MoneyFormat.format(result.totalPaid()) + "</white>.");
        if (result.fee().compareTo(java.math.BigDecimal.ZERO) > 0) {
            MessageUtils.send(player, "<gray>Corretagem paga: <yellow>$" + MoneyFormat.format(result.fee()));
        }
        MessageUtils.playSuccess(player);

        // Reabre o menu de detalhe com dados atualizados
        Bukkit.getScheduler().runTaskLater(plugin, () -> menuService.openCompanyDetail(player, stockId), 5L);
    }

    private void onSellResult(Player player, SellResult result, String stockId) {
        if (!result.success()) {
            String msg = switch (result.reason()) {
                case "insufficient-holding" -> "Você não possui ações suficientes para vender.";
                case "invalid-quantity"     -> "Quantidade inválida.";
                default -> "Erro ao processar venda: " + result.reason();
            };
            MessageUtils.sendError(player, msg);
            return;
        }

        String profitColor = result.profit().compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";
        MessageUtils.send(player,
                "<green>✔ Você vendeu <white>" + result.qty() + " ação(ões)</white> de " +
                "<aqua>" + stockId + "</aqua> e recebeu <white>$" + MoneyFormat.format(result.net()) + "</white>.");
        MessageUtils.send(player,
                "<gray>Lucro/Prejuízo: " + profitColor + "$" + MoneyFormat.format(result.profit()));
        if (result.fee().compareTo(java.math.BigDecimal.ZERO) > 0) {
            MessageUtils.send(player, "<gray>Corretagem: <yellow>$" + MoneyFormat.format(result.fee()));
        }
        MessageUtils.playSuccess(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> menuService.openCompanyDetail(player, stockId), 5L);
    }
}
