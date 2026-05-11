package me.scoltbr.scoltEconomys.admin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class AdminMenuListener implements Listener {

    private final AdminMenuService menus;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public AdminMenuListener(AdminMenuService menus) {
        this.menus = menus;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof AdminMenuHolder holder)) return;

        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;
        Material type = e.getCurrentItem().getType();
        
        // Som padrão
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

        switch (holder.page()) {
            case MAIN -> handleMain(p, e.getInventory(), type, e.getSlot());
            case ALERTS -> handleAlerts(p, e.getInventory(), type, e.getSlot());
            case TAX -> handleTax(p, e, e.getInventory(), type, e.getSlot());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AdminMenuHolder) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof AdminMenuHolder && e.getPlayer() instanceof Player player) {
            menus.cancelLiveRefresh(player);
        }
    }

    private void handleMain(Player p, org.bukkit.inventory.Inventory inv, Material type, int slot) {
        if (slot == 53) {
            p.closeInventory();
            return;
        }

        if (slot == 46 || type == Material.PAPER) {
            menus.openTax(p);
            return;
        }

        if (slot == 31 || slot == 48 || type == Material.BELL || type == Material.RED_DYE || type == Material.LIME_DYE) {
            menus.openAlerts(p);
        }
    }

    private void handleAlerts(Player p, org.bukkit.inventory.Inventory inv, Material type, int slot) {
        if (slot == 53) {
            p.closeInventory();
            return;
        }
        if (slot == 45) {
            menus.openMain(p);
        }
    }

    private void handleTax(Player p, InventoryClickEvent e, org.bukkit.inventory.Inventory inv, Material type, int slot) {
        if (slot == 53) {
            p.closeInventory();
            return;
        }
        if (slot == 45) {
            menus.openMain(p);
            return;
        }

        // Só responde aos cards de imposto (Slots 20 e 24)
        if (slot != 20 && slot != 24) return;

        var taxType = (slot == 20)
                ? me.scoltbr.scoltEconomys.tax.TaxType.TRANSFER
                : me.scoltbr.scoltEconomys.tax.TaxType.WITHDRAW;

        // Q = toggle
        if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP) {
            menus.toggleTax(p, taxType);
            menus.refreshSilently(inv);
            return;
        }

        double delta = 0.0;
        boolean shift = e.isShiftClick();

        if (e.isLeftClick()) delta = shift ? 0.01 : 0.001;     // +1% ou +0.1%
        else if (e.isRightClick()) delta = shift ? -0.01 : -0.001;

        if (delta != 0.0) {
            menus.adjustTaxRate(p, taxType, delta);
            menus.refreshSilently(inv);
        }
    }
}
