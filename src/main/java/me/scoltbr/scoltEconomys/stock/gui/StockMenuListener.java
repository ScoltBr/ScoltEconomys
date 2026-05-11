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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Listener que intercepta cliques nos menus do Mercado de Ações.
 */
public final class StockMenuListener implements Listener {

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

        // Verifica Ação embutida no botão
        String rawAction = meta.getPersistentDataContainer().get(StockMenuService.STOCK_ACTION_KEY, PersistentDataType.STRING);
        if (rawAction != null) {
            handleAction(player, holder, rawAction);
            return;
        }

        // Verifica Stock ID embutido no botão (para clicar em uma empresa na lista)
        String stockId = meta.getPersistentDataContainer().get(StockMenuService.STOCK_ID_KEY, PersistentDataType.STRING);
        if (stockId != null) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            menuService.openCompanyDetail(player, stockId);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof StockMenuHolder && event.getPlayer() instanceof Player player) {
            menuService.cancelLiveRefresh(player);
        }
    }

    private void handleAction(Player player, StockMenuHolder holder, String rawAction) {
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        // Ações de Navegação
        switch (rawAction) {
            case "CLOSE" -> {
                player.closeInventory();
                return;
            }
            case "BACK_TO_MARKET" -> {
                menuService.openMarket(player, 1);
                return;
            }
            case "BACK_TO_DETAIL" -> {
                if (holder.stockId() != null) menuService.openCompanyDetail(player, holder.stockId());
                return;
            }
            case "OPEN_PORTFOLIO" -> {
                menuService.openPortfolio(player);
                return;
            }
            case "OPEN_TOP" -> {
                if (holder.stockId() != null) menuService.openTopHolders(player, holder.stockId());
                return;
            }
            case "PAGE_NEXT" -> {
                menuService.openMarket(player, holder.page() + 1);
                return;
            }
            case "PAGE_PREV" -> {
                menuService.openMarket(player, Math.max(1, holder.page() - 1));
                return;
            }
        }

        // Ações Transacionais (BUY/SELL)
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
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    MessageUtils.sendError(player, "Você não possui ações desta empresa.");
                    return;
                }
                stockService.sellAsync(player.getUniqueId(), stockId, -1,
                        result -> onSellResult(player, result, stockId));
            }
        }
    }

    private void onBuyResult(Player player, BuyResult result, String stockId) {
        if (!result.success()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            String msg = switch (result.reason()) {
                case "insufficient-funds"   -> "Saldo insuficiente na carteira.";
                case "insufficient-supply"  -> "Ações insuficientes disponíveis no mercado.";
                case "invalid-quantity"     -> "Quantidade inválida.";
                default -> "Erro ao processar compra: " + result.reason();
            };
            MessageUtils.sendError(player, msg);
            return;
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        MessageUtils.send(player,
                "<green>✔ Você comprou <white>" + result.qty() + " ação(ões)</white> de " +
                "<aqua>" + stockId + "</aqua> por <white>$" + MoneyFormat.format(result.totalPaid()) + "</white>.");
        if (result.fee().compareTo(java.math.BigDecimal.ZERO) > 0) {
            MessageUtils.send(player, "<gray>Corretagem paga: <yellow>$" + MoneyFormat.format(result.fee()));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof StockMenuHolder h 
                && h.type() == StockMenuHolder.MenuType.COMPANY_DETAIL) {
                menuService.openCompanyDetail(player, stockId); // refresh
            }
        }, 5L);
    }

    private void onSellResult(Player player, SellResult result, String stockId) {
        if (!result.success()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            String msg = switch (result.reason()) {
                case "insufficient-holding" -> "Você não possui ações suficientes para vender.";
                case "invalid-quantity"     -> "Quantidade inválida.";
                default -> "Erro ao processar venda: " + result.reason();
            };
            MessageUtils.sendError(player, msg);
            return;
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        String profitColor = result.profit().compareTo(java.math.BigDecimal.ZERO) >= 0 ? "<green>" : "<red>";
        MessageUtils.send(player,
                "<green>✔ Você vendeu <white>" + result.qty() + " ação(ões)</white> de " +
                "<aqua>" + stockId + "</aqua> e recebeu <white>$" + MoneyFormat.format(result.net()) + "</white>.");
        MessageUtils.send(player,
                "<gray>Lucro/Prejuízo: " + profitColor + "$" + MoneyFormat.format(result.profit()));
        if (result.fee().compareTo(java.math.BigDecimal.ZERO) > 0) {
            MessageUtils.send(player, "<gray>Corretagem: <yellow>$" + MoneyFormat.format(result.fee()));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof StockMenuHolder h 
                && h.type() == StockMenuHolder.MenuType.COMPANY_DETAIL) {
                menuService.openCompanyDetail(player, stockId); // refresh
            }
        }, 5L);
    }
}
