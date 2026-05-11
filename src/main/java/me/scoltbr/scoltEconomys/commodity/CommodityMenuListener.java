package me.scoltbr.scoltEconomys.commodity;

import me.scoltbr.scoltEconomys.util.MessageUtils;
import me.scoltbr.scoltEconomys.util.MoneyFormat;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class CommodityMenuListener implements Listener {

    private final CommodityMenuService menus;
    private final CommodityMarketService market;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public CommodityMenuListener(CommodityMenuService menus, CommodityMarketService market) {
        this.menus = menus;
        this.market = market;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof CommodityMenuHolder holder)) return;

        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;
        ItemStack clicked = e.getCurrentItem();
        int slot = e.getSlot();

        // Navegação e botões utilitários
        if (slot == 45 && clicked.getType() == org.bukkit.Material.PLAYER_HEAD) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            menus.openMenu(p, holder.page() - 1);
            return;
        }
        if (slot == 53 && clicked.getType() == org.bukkit.Material.PLAYER_HEAD) {
            if (clicked.getItemMeta() != null && PLAIN.serialize(clicked.getItemMeta().displayName()).contains("Próxima")) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                menus.openMenu(p, holder.page() + 1);
            } else {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                p.closeInventory();
            }
            return;
        }
        if (slot == 49 && clicked.getType() == org.bukkit.Material.PLAYER_HEAD) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            p.closeInventory();
            return;
        }

        // Clicou numa commodity?
        if (clicked.getItemMeta() == null || clicked.getItemMeta().lore() == null) return;
        
        // Vamos extrair o ID pelo lore (última linha)
        var loreList = clicked.getItemMeta().lore();
        String lastLine = PLAIN.serialize(loreList.get(loreList.size() - 1));
        
        if (!lastLine.contains("ID:")) return;
        String id = lastLine.substring(lastLine.indexOf("ID:") + 3).trim();

        int qtyToSell = e.isShiftClick() ? -1 : 1;
        boolean isSell = e.isLeftClick();
        boolean isBuy = e.isRightClick();
        
        CommodityTransactionResult result;
        
        if (isSell) {
            result = market.sell(p, id, qtyToSell);
        } else if (isBuy) {
            // Se for Shift+Direito, tenta comprar o maxPerTransaction ou o que o dinheiro der?
            // Vamos simplificar: Shift+Direito = compra 64, Direito = compra 1. 
            // O market.buyPhysical vai checar os limites.
            int qtyToBuy = e.isShiftClick() ? 64 : 1;
            result = market.buyPhysical(p, id, qtyToBuy);
        } else {
            return;
        }

        Commodity c = market.getCommodity(id).orElse(null);
        if (c == null) return;

        if (!result.success()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            String msg = switch (result.reason()) {
                case "no-items"           -> "Você não possui <bold>" + c.material().name() + "</bold> no inventário.";
                case "invalid-quantity"   -> "Quantidade inválida.";
                case "account-not-cached" -> "Erro ao acessar sua conta. Reconecte-se.";
                case "insufficient-funds" -> "Você não possui dinheiro suficiente.";
                case "no-space"           -> "Inventário cheio.";
                default -> result.reason().startsWith("exceeds-limit-")
                        ? "Limite por transação: <bold>" + c.maxPerTransaction() + " unidades</bold>."
                        : result.reason().startsWith("insufficient-items")
                        ? "Você não possui itens suficientes."
                        : "Erro: " + result.reason();
            };
            MessageUtils.sendError(p, msg);
            return;
        }

        // Sucesso
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        if (isSell) {
            MessageUtils.actionBar(p, "<gradient:#00ffa1:#0099ff>+$ " + MoneyFormat.format(result.total()) + " — " + result.quantity() + "x " + c.displayName() + "</gradient>");
        } else {
            MessageUtils.actionBar(p, "<gradient:#ff3300:#ff9900>-$ " + MoneyFormat.format(result.total()) + " — Comprado " + result.quantity() + "x " + c.displayName() + "</gradient>");
        }
        
        // Atualiza a tela instantaneamente pra refletir caso algo mude (embora só vá mudar no ticker)
        menus.renderPage(e.getInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof CommodityMenuHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof CommodityMenuHolder && e.getPlayer() instanceof Player player) {
            menus.cancelLiveRefresh(player);
        }
    }
}
